package ru.easyxbrl.parse_taxonomy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.EntryPointElement;
import ru.easyxbrl.core.XbrlTable;

public class LoadTaxonomyFromZip {
	
	static public final String XSD_SCHEMA_ROOT = "schema"; // в ep_nso_purcb_m_30d.xsd - имя корневого элемента (без префикса)
	static public final String XSD_ENTRYPOINT_ELEMENT = "import"; // в ep_nso_purcb_m_30d.xsd - имя элемента (без префикса)

	static public final String XSD_LABEL_ROOT = "linkbase"; // в ep_nso_purcb_m_30d.xsd - имя корневого элемента (без префикса)
	
	static public final String WWW_CBR_RU = "http://www.cbr.ru/"; // префикс, обозначающий ссылку на элемент таксономии Центрального Банка  
	
	
	// допустимые значения namespace для элементов xsd:import в файлах точек входа, иначе выдаётся предупреждение
	static public final Set<String> validXbrlUrl = new HashSet<>() {
		private static final long serialVersionUID = -6179160411648955894L;

		{
			add("http://www.xbrl.org/2003/instance");
			add("http://xbrl.org/2010/message");
			add("http://xbrl.org/2010/message/validation");
			add("http://xbrl.org/2008/validation");
			add("http://xbrl.org/2016/assertion-severity");
		}
	};
	
	// префикс для файлов, совпадает с именем архива
	static public String rootPrefix = null;
	
	// хранение имён файлов и их содержимого
	static public final Map<String, ZipEntry> zipFilenames = new HashMap<>();
	static public final Map<ZipEntry,byte[]> zipFiles = new HashMap<>();
	
	// прямое и обратное преобразование путей
	static public final Catalog catalog = new Catalog();
	
	// соотношение между namespaces и schemaLocation (обработка точек входа)
	static public final Map<String, String> nsToLocation = new HashMap<>();
	
	// список таблиц
	static public final List<XsdOkudTable> nsOkudTable= Collections.synchronizedList(new ArrayList<>());
	
	static private Core core;
	
	static final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	
	/**
	 * Сброс значений таблиц и переменных
	 */
	public static void clear() {
		rootPrefix = null;
		zipFilenames.clear();
		zipFiles.clear();
		catalog.clear();
		nsToLocation.clear();
		nsOkudTable.clear();
	}
	
	/**
	 * Извлечение данных из архива официальной таксономии и перенос во внутреннее ядро
	 * @param is
	 * @throws Exception
	 */
	static public void parse(Core core, InputStream is) throws Exception {
		
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "4");

		dbf.setValidating(false); // без интернета происходит ошибка java.net.UnknownHostException: www.oasis-open.org
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		

		// очищаем данные таксономии, используемые массивы
		core.clear();
		LoadTaxonomyFromZip.core = core;
		
		clear();
		
		// извлечение из архива файлов в Map
		loadFilesFromZipToMap(is);

		// обработка description.xml
		parseDescriptionXml();
		
		// обработка catalog.xml 
		parseCatalogXml();
		
		// обработка entry_point.xml + taxonomyPackage.xml, 
		// заполняем таблицу соответствий namespace и абсолютного пути к файлу (nsToLocation)
		parseEntryPoints();
		
		// обходим все таблицы из nsToLocation
		// заполняем карту соответствий namespace:XsdOkudTable, формируем таблицу соответствий nsOkudTable
		XsdOkudTable.parseFromNsToLocation();
		
		
		// обходим nsOkudTable, формируем список словарей
		// далее создаём элементы словарей, подключаем к ним метки, создаём списки и т.п.
		Dictionary.parse(core);
		
		// обходим таблицы, обрабатываем definition && presentation
		{
			long startTime = System.currentTimeMillis();
			System.out.println("Обработка definition & presentation");
			
			// синхронизированные списки для параллельной обработки
			final Map<String, XbrlTable> tables = Collections.synchronizedMap(core.tables);
			final Map<String, String> location = Collections.synchronizedMap(core.location);
			final Map<String, String> revLocation = Collections.synchronizedMap(core.revLocation);

			final Queue<String> removeTables = new ConcurrentLinkedQueue<>(); // таблицы на удаление из точек входа (т.к. словари)

			nsOkudTable.parallelStream().forEach( x -> {
				
				// для всех таблиц добавляем соотношение между namespace и location
				location.put(x.targetNamespace, catalog.pathToUri(x.getLocation()));
				revLocation.put(catalog.pathToUri(x.getLocation()), x.targetNamespace);
				
				// Если в таблице есть элементы, то это словарь, а не таблица отчётности
				if (x.elements.isEmpty()) {

					final XbrlTable tbl = new XbrlTable(x.targetNamespace);
					tables.putIfAbsent(x.targetNamespace, tbl);

					// вносим информацию из RoleType в таблицу
					// если в дальнейшем role раздела definition будет отсутствовать в roleType
					// то будем считать этот раздел списком!
					
					x.roleType.forEach( r -> {
						tbl.putRoleUri(x.targetNamespace, r.id, r.roleURI, r.definition);
					});
					
					// обрабатываем definition-файл
					x.definition.forEach( d-> {
						try {
							final XsdSchemaDefinition def = new XsdSchemaDefinition(core, d, Dictionary.dicts, "definitionLink", "definitionArc", tbl);
							def.defList.forEach( defElement -> {

								tbl.definitions.put(defElement.role, defElement);
								
							});
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					
					x.presentation.forEach( d-> {
						try {
							final XsdSchemaDefinition def = new XsdSchemaDefinition(core, d, Dictionary.dicts, "presentationLink", "presentationArc", tbl);
							def.defList.forEach( defElement -> {
								
								tbl.presentation.put(defElement.role, defElement);
								
							});
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
					
					// Обработка локальных меток
					x.label.forEach( l-> {
						//System.out.println("   lbl " + x.targetNamespace + " " + l);
						try {
							new XsdSchemaLabel(core, l, Dictionary.dicts, tbl.labels);
						} catch (Exception e) {
							e.printStackTrace();
						}
					});

				} else {
					// вносим в список на удаление из таблиц точек входа
					removeTables.add(x.targetNamespace);
				}
				
			});


			// удаляем из точек входа, если найдутся
			core.entryPoints.forEach((k,v) -> v.tables.removeAll(removeTables));
			
			
			System.out.println("    обработано: "+nsOkudTable.size()+" (" + (System.currentTimeMillis() - startTime) + " ms).");
			
		}
		
		
		// очищаем массивы
		clear();
		
		// закрываем поток
		is.close();
	}

	
	/**
	 * Загрузка файлов из таксономии во внутреннюю базу
	 * @param is
	 * @param engine
	 * @throws Exception 
	 */
	static public void loadFilesFromZipToMap(InputStream is) throws Exception {
		
		zipFilenames.clear();
		zipFiles.clear();
		long startTime = System.currentTimeMillis();

		// считываем все файлы таксономии в память (files)
		try (ZipInputStream zip = new ZipInputStream(is)) {
			System.out.println("Начало загрузки файлов таксономии из потока.");
    		ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
            	final String entryName = entry.getName().replace("\\", "/");
            	zipFilenames.put(entryName, entry);
            	if (rootPrefix == null) {
            		final int startPosition = entryName.indexOf("/");
            		if (startPosition == -1) {
            			rootPrefix = "";
            		} else {
                		rootPrefix = entryName.substring(0, startPosition + 1);
            		}
            	} else {
            		if (!entry.getName().startsWith(rootPrefix)) System.out.println(entry.getName());
            	}
            	
            	zipFiles.put(entry, zip.readAllBytes());
            }
            //System.out.println("rootPrefix >> "+rootPrefix);
            System.out.println("    Успешное окончание загрузки (" + (System.currentTimeMillis() - startTime) + " ms).");
		}
	}

	/**
	 * Обработка description.xml
	 * @throws IOException
	 */
	static public void parseDescriptionXml() throws IOException {
    	
    	final DocumentBuilder documentBuilder;
		long startTime = System.currentTimeMillis();
		
		final String description = rootPrefix+"description.xml";
		if (zipFilenames.containsKey(description)) {
			final ByteArrayInputStream ba = new ByteArrayInputStream(zipFiles.get(zipFilenames.get(description)));
	    	try {
				System.out.println("Начало обработки "+description);

	    		documentBuilder = dbf.newDocumentBuilder();
				final Document document = documentBuilder.parse(ba);
				final Node taxonomyDescription = document.getDocumentElement();
				final NodeList par = taxonomyDescription.getChildNodes();
				for (int i = 0; i < par.getLength(); i++) {
	                final Node el = par.item(i);
					if (el.getNodeType() == Node.TEXT_NODE) continue;
					
					final String text = el.getTextContent();

					switch (el.getNodeName()) {
					case "Version":
						core.info.version = text;
						break;
					case "DateBegin":
						core.info.dateBegin = "".equals(text) ? null : LocalDateTime.parse(text);
						break;
					case "DateEnd":
						core.info.dateEnd = "".equals(text) ? null : LocalDateTime.parse(text);
						break;
					case "DatePublic":
						core.info.datePublic = "".equals(text) ? null : LocalDateTime.parse(text);
						break;
					case "Comments":
						core.info.comments = text;
						break;
					default:
						System.out.println("Ошибка! Неизвестный атрибут: " + el.getNodeName());
					}
				}
	    	} catch (ParserConfigurationException | SAXException e) {
	    		throw new RuntimeException("Ошибка обработки "+description);
			}
		} else {
			System.out.println("Ошибка! "+description+" не обнаружен");
		}

		System.out.println("    "+core.info.version + "; " + core.info.comments + " (" + (System.currentTimeMillis() - startTime) + " ms).");
	}
	

	/**
	 * Заполнение карт преобразования из URL в путь и обратно
	 * @throws IOException
	 */
	static public void parseCatalogXml() throws IOException {
		
		catalog.clear();

		long startTime = System.currentTimeMillis();
		
		final String catalogPrefix = rootPrefix+"META-INF/catalog.xml";
		if (zipFilenames.containsKey(catalogPrefix)) {
			final ByteArrayInputStream ba = new ByteArrayInputStream(zipFiles.get(zipFilenames.get(catalogPrefix)));
			
			try {
				System.out.println("Начало обработки "+catalogPrefix);
	    		
				final Document document = dbf.newDocumentBuilder().parse(ba);

				final NodeList uri = document.getElementsByTagName("rewriteURI");
				for (int i = 0; i < uri.getLength(); i++) {
	                final Node el = uri.item(i);
					if (el.getNodeType() == Node.TEXT_NODE) continue;
	                final NamedNodeMap nnm = el.getAttributes();
	                catalog.addUriAndPath(
	                		nnm.getNamedItem("uriStartString").getTextContent(), 
	                		nnm.getNamedItem("rewritePrefix").getTextContent(),
	                		catalogPrefix
	                );
				}

	    	} catch (ParserConfigurationException | SAXException e) {
	    		throw new RuntimeException("Ошибка обработки "+catalogPrefix);
			}
		} else {
			System.out.println("Ошибка! "+catalogPrefix+" не обнаружен");
		}

		System.out.println("    элементов: " + catalog.getItems().values().size()+ " (" + (System.currentTimeMillis() - startTime) + " ms).");
	
	}
	

	static public void parseEntryPoints() throws IOException {
		
		// временный список EntryPointList, далее передаем его в TaxonomyInfo
		final List<EntryPointElement> epList = new ArrayList<>();

		long startTime = System.currentTimeMillis();
		System.out.println("Начало обработки entry_point.xml + taxonomyPackage.xml");
		
    	{
    		final String entry_point = rootPrefix+"META-INF/entry_point.xml";
    		if (zipFilenames.containsKey(entry_point)) {
    			final ByteArrayInputStream ba = new ByteArrayInputStream(zipFiles.get(zipFilenames.get(entry_point)));
    	    	try {
					final Document document = dbf.newDocumentBuilder().parse(ba);
					final NodeList entryPoints = document.getElementsByTagName("EntryPoint");
					for (int i = 0; i < entryPoints.getLength(); i++) {
		                final Node element = entryPoints.item(i);
    					if (element.getNodeType() == Node.TEXT_NODE) continue;
    					
    					EntryPointElement epe = new EntryPointElement();
    					
    					final NodeList nl = element.getChildNodes();
    					for (int j = 0; j < nl.getLength(); j++) {
    		                final Node el = nl.item(j);
        					if (el.getNodeType() != Node.ELEMENT_NODE) continue;
        					
        					final String text = el.getTextContent();
        					
    						switch (el.getNodeName()) {
    						case "NFOType":
    							epe.nfoType = text;
    							break;
    						case "ReportType":
    							epe.reportType = text;
    							break;
    						case "ReportPeriodType":
    							epe.reportPeriodType = text;
    							switch(text) {
    							case "y":
    							case "q":
    							case "w":
    							case "m":
    							case "r":
    								break;
    							default:
    								System.out.println("Ошибка! "+entry_point+" неизвестный тип периода " + text);
    							}
    							break;
    						case "PathToXsd":
    							epe.entryPointDocument = catalog.pathToUri(Catalog.changePath(entry_point, text)); // преобразуем в абсолютный путь и далее в точку входа
    							break;
    						case "NFOTypeRus":
    							epe.nfoTypeRus = text;
    							break;
    						case "ReportTypeRus":
    							epe.reportTypeRus = text;
    							break;
    						case "ReportPeriodTypeRus":
    							epe.reportPeriodTypeRus = text;
    							break;
    						default:
    							System.out.println("Ошибка! "+entry_point+" неизвестный атрибут " + el.getNodeName());
    						}
    					}
    					
						//System.out.println(epe.entryPointDocument + " -> " + catalog.uriToPath(epe.entryPointDocument) + " " + epe.reportTypeRus);
						
    					epList.add(epe);
					}
    	    	} catch (ParserConfigurationException | SAXException e) {
    	    		throw new RuntimeException("Ошибка обработки "+entry_point);
    			}
    		} else {
    			System.out.println("Ошибка! "+entry_point+" не обнаружен");
    		}
    	}

    	{
    		final String taxonomyPackage = rootPrefix+"META-INF/taxonomyPackage.xml";
    		if (zipFilenames.containsKey(taxonomyPackage)) {
    			final ByteArrayInputStream ba = new ByteArrayInputStream(zipFiles.get(zipFilenames.get(taxonomyPackage)));
    	    	try {
					final Document document = dbf.newDocumentBuilder().parse(ba);
					{
						NodeList nl = document.getElementsByTagName("tp:identifier");
						core.info.tpIdentifier = nl.item(0).getTextContent();
					}
					{
						NodeList nl = document.getElementsByTagName("tp:name");
						core.info.tpName = nl.item(0).getTextContent();
					}
					{
						NodeList nl = document.getElementsByTagName("tp:version");
						core.info.tpVersion = nl.item(0).getTextContent();
					}
					
					final NodeList ep = document.getElementsByTagName("tp:entryPoint");
					for (int i = 0; i < ep.getLength(); i++) {
		                final Node element = ep.item(i);
    					if (element.getNodeType() == Node.TEXT_NODE) continue;
    					
    					EntryPointElement epe = new EntryPointElement();
    					
    					final NodeList nl = element.getChildNodes();
    					for (int j = 0; j < nl.getLength(); j++) {
    		                final Node el = nl.item(j);
        					if (el.getNodeType() != Node.ELEMENT_NODE) continue;
        					
    						switch (el.getNodeName()) {
    						case "tp:name":
    							epe.tpName = el.getTextContent();
    							break;
    						case "tp:description":
    							epe.tpDescription = el.getTextContent();
    							break;
    						case "tp:entryPointDocument":
    							epe.entryPointDocument = el.getAttributes().getNamedItem("href").getTextContent();
    							break;
    						default:
    							System.out.println("Ошибка! "+taxonomyPackage+" неизвестный атрибут " + el.getNodeName());
    						}
    					}
    					
    					for (EntryPointElement epElement:epList) {
    						if (epElement.entryPointDocument.equals(epe.entryPointDocument)) {
    							epElement.tpName = epe.tpName;
    							epElement.tpDescription = epe.tpDescription;
    							break;
    						}
    					}
    					
					}
    	    	} catch (ParserConfigurationException | SAXException e) {
    	    		throw new RuntimeException("Ошибка обработки "+taxonomyPackage);
    			}
    		} else {
    			System.out.println("Ошибка! "+taxonomyPackage+" не обнаружен");
    		}
    	}
    	
    	// Заполняем используемые таблицы для точек входа, заполняем таблицу соответствий namespace и абсолютного пути к файлу (nsToLocation)
    	fillEntryPointsTables(epList);
		
    	// Переносим точки входа в ядро таксономии
    	epList.forEach( ep -> core.entryPoints.put(ep.entryPointDocument, ep));
		
		System.out.println("    элементов: " + core.entryPoints.size()+ " (" + (System.currentTimeMillis() - startTime) + " ms).");
	
	}
	

	/**
	 * Находим требуемые таблицы для точки входа
	 * @param ep
	 */
	private static void fillEntryPointsTables(List<EntryPointElement> epList) {
		epList.forEach( ep -> {
			final String path = catalog.uriToPath(ep.entryPointDocument);
			final InputStream is = new ByteArrayInputStream(zipFiles.get(zipFilenames.get(path)));
			try {
				Document document = dbf.newDocumentBuilder().parse(is);
				final String prefix = getSchemaPrefix(XSD_SCHEMA_ROOT, document);
				//System.out.println(path + "  " + prefix);
				
				final NodeList nl = document.getElementsByTagName(prefix+XSD_ENTRYPOINT_ELEMENT);
				for (int i=0; i<nl.getLength(); i++) {
					if (nl.item(i).getNodeType() == Node.TEXT_NODE) continue;
					
					if (nl.item(i).hasAttributes()) {
						final String ns = nl.item(i).getAttributes().getNamedItem("namespace").getTextContent();
						if (ns.startsWith(WWW_CBR_RU)) {
							ep.tables.add(ns);
							
							final String sl = Catalog.changePath(path, nl.item(i).getAttributes().getNamedItem("schemaLocation").getTextContent());
							//System.out.println(ns + " -> " + sl + " >> " + path);
							
							//заполняем таблицу соответствий namespace и абсолютного пути к файлу
							nsToLocation.put(ns, sl);
						} else {
							if (!validXbrlUrl.contains(ns)) {
								System.out.println("Ошибка! Неизвестный элемент " + path + " -> " + ns);
							}
						}
					} else {
						System.out.println("Ошибка! Отсутствуют атрибуты у эелемента " + path);
					}
					//возможно, освободит дополнительно немного памяти
					//((ArrayList<String>)ep.tables).trimToSize();
				}
				
			} catch (SAXException | IOException | ParserConfigurationException e) {
				e.printStackTrace();
			}
		});
	}
	
	/**
	 * Возращаем префикс элементов в документе
	 * @param schema
	 * @param document
	 * @return
	 */
	public static String getSchemaPrefix(String schema, Document document) {
		String prefix = "";
		
		for (int i=0; i< document.getChildNodes().getLength(); i++) {
			if (document.getChildNodes().item(i).getNodeType() == Node.TEXT_NODE) continue;
			
			if (document.getChildNodes().item(i).getNodeName().endsWith(schema)) {
				prefix = document.getChildNodes().item(i).getNodeName().
						substring(0, document.getChildNodes().item(i).getNodeName().length() - schema.length());
				break;
			}
		}
		
		return prefix;
	}

	/**
	 * Возращаем корневой элемент в документе
	 * @param schema
	 * @param document
	 * @return
	 */
	public static Node getMainSchema(String schema, Document document) {
		Node node = null;
		for (int i=0; i< document.getChildNodes().getLength(); i++) {
			if (document.getChildNodes().item(i).getNodeType() == Node.TEXT_NODE) continue;
			
			if (document.getChildNodes().item(i).getNodeName().endsWith(schema)) {
				node = document.getChildNodes().item(i);
				break;
			}
		}
		return node;
	}
	
}
