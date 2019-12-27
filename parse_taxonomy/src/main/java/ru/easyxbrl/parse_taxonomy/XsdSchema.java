package ru.easyxbrl.parse_taxonomy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ru.easyxbrl.core.XbrlElement;
import ru.easyxbrl.define.XbrlPeriodType;
import ru.easyxbrl.define.XbrlSubstitutionGroup;
import ru.easyxbrl.define.XbrlType;
import ru.easyxbrl.define.XbrlTypeBalance;

public abstract class XsdSchema {

	/*
	 
	 в заголовке: targetNamespace="http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409"
	 в аннотации: link:roleType roleURI="http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409/SR_0420409" id="SR_0420409"
	 
	 т.е. http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409/SR_0420409 = http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409#SR_0420409
	 
	 1. обрабатываем заголовок файла ( SR_0420417.xsd )
	 2. обрабатываем xsd:annotation / xsd:appinfo : link:linkbaseRef, link:roleType
	 3. обрабатываем import
	 
	 */
	
	private String location = null;            // путь к файлу
	
	public String targetNamespace = null;     // http://www.cbr.ru/xbrl/nso/purcb/rep/2018-12-31/tab/SR_0420409
	public String elementFormDefault = null;  // qualified
	
	public List<String> presentation = new ArrayList<>();  // полный путь к файлу-презентации (linkbaseRef)
	public List<String> definition   = new ArrayList<>();  // полный путь к файлу-определений (linkbaseRef)
	public List<String> label        = new ArrayList<>();  // полный путь к файлу-определений (linkbaseRef) - может быть несколько!
	public List<String> reference    = new ArrayList<>();  // полный путь к файлу-определений (linkbaseRef)
	
	// таблица соответствий префикс - схема
	public final Map<String, String> ns = new HashMap<>(); // xmlns:purcb-dic="http://www.cbr.ru/xbrl/nso/purcb/dic/purcb-dic"
	public String linkPrefix = "";                        // если попадается ссылка на http://www.xbrl.org/2003/linkbase, то запоминаем префикс отдельно
	public String xlinkPrefix = "";                       // если попадается ссылка на http://www.w3.org/1999/xlink, то запоминаем префикс отдельно
	
	// список требуемых справочников таксономии
	public final List<ImportElement> importElements = new ArrayList<>(); // элементы import namespace="http://www.cbr.ru/xbrl/udr/dim/dim-int" schemaLocation="../../../../../../udr/dim/dim-int.xsd"

	// список элементов справочника
	public final List<XbrlElement> elements = new ArrayList<>(); // элементы import namespace="http://www.cbr.ru/xbrl/udr/dim/dim-int" schemaLocation="../../../../../../udr/dim/dim-int.xsd"
	
	// список описания секций отчётности 
	public final List<XsdRole> roleType = new ArrayList<>();
	
	/**
	 * Основной конструктор
	 * @param location
	 * @throws Exception
	 */
	
	
	public XsdSchema(String location) throws Exception {
		this.location = location;
		
		
		
		//if (!location.endsWith("uk-dic.xsd")) return;

		
		
		try {
			final ByteArrayInputStream ba = new ByteArrayInputStream(LoadTaxonomyFromZip.zipFiles.get(LoadTaxonomyFromZip.zipFilenames.get(location)));
			
			final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ba);
			
			final String prefix = LoadTaxonomyFromZip.getSchemaPrefix(LoadTaxonomyFromZip.XSD_SCHEMA_ROOT, document);
			final String xsdAnnotation = prefix + "annotation";
			final String xsdImport = prefix + "import";
			final String xsdElement = prefix + "element";
			
			final NodeList schema = document.getElementsByTagName(prefix + LoadTaxonomyFromZip.XSD_SCHEMA_ROOT);
			
			//System.out.println( "XsdSchema  " + location);
			
			// начинаем заполнять структуру точки входа
			if (schema != null && schema.getLength() > 0) {

				final Node node = schema.item(0);
				
				// обрабатываем заголовок
				parseHeader(node);

				// выбираем все подчинённые элементы
				// обрабатываем: xsd:annotation, xsd:import
				final NodeList importList = node.getChildNodes();

				for (int i = 0; i < importList.getLength(); i++) {
					
					final Node el = importList.item(i);
					
					if (el.getNodeType() == Node.TEXT_NODE) continue;
					
					// встречается чаще, поэтому первым сравнением
					if (xsdElement.equals(el.getNodeName())) {
						
						parseElement(el);
						
					} else if (xsdImport.equals(el.getNodeName())) {
						
						parseImport(el);
						
					} else if (xsdAnnotation.equals(el.getNodeName())) {
						
						parseAnnotation(el, prefix);
						
					} 
				}
			}
				

		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new RuntimeException("Ошибка обработки " + location);
		}
		
	}
	
	/**
	 * Обрабатываем заголовок схемы, получаем ns-префиксы
	 * @param node
	 * @throws Exception
	 */
	private void parseHeader(Node node) throws Exception {
		// получаем атрибуты схемы
		// без атрибутов не работаем
		if ( node.hasAttributes() ) {
			final NamedNodeMap attr = node.getAttributes();
			
			for (int i=0; i < attr.getLength(); i++) {
				
				final String itemName = attr.item(i).getNodeName();
				final String text = attr.item(i).getTextContent();
				
				switch (itemName) {
				case "elementFormDefault":
					elementFormDefault = text;
					break;
				case "targetNamespace":
					targetNamespace = text;
					break;
				case "attributeFormDefault":
					// пропускаем
					break;
				default:
					if (itemName.startsWith("xmlns:")) {
						
						// помещаем префиксы в таблицу соответствий
						ns.put(itemName.substring(6), text);

						// отдельно запоминаем префиксы link и xlink
						if ("http://www.xbrl.org/2003/linkbase".equals(text)) linkPrefix = itemName.substring(6) + ":"; 
						if ("http://www.w3.org/1999/xlink".equals(text)) xlinkPrefix = itemName.substring(6) + ":"; 
						
						if(
							!text.toLowerCase().startsWith("http://") &&
							!text.toLowerCase().startsWith("htttp://") &&
							!text.toLowerCase().startsWith("https://") &&
							!text.equals("URN:FIXME") ) {
							System.out.println( "   " + location + " xmlns >> " + itemName + " - " + text);
							throw new Exception("XsdOkudTable - ошибка в заголовочном файле, " + location);
						}
						
					} else if ("xmlns".equals(itemName) && "http://www.w3.org/2001/XMLSchema".equals(text)){
						// не обрабатываем стандартное обозначение
					} else if ("xmlns".equals(itemName) && "http://www.xbrl.org/2003/linkbase".equals(text)){
						// не обрабатываем стандартное обозначение
					} else {
						System.out.println("    Ошибка! " + location + " неизвестный атрибут " + itemName + " - " + text); 
					}
					break;
				}
			}
		} else {
			System.out.println(location + " атрибуты отсутствуют у " + LoadTaxonomyFromZip.XSD_SCHEMA_ROOT); 
		}
	}



	/**
	 * Обрабатываем аннотацию, получаем ссылки на definition, presentation, описания для секций отчётности
	 * @param node
	 * @param prefix
	 */
	private void parseAnnotation(Node node, String prefix) {
		
		final NodeList annotation = node.getChildNodes();
		final String linkbaseRef = linkPrefix+"linkbaseRef";
		final String roleType = linkPrefix+"roleType";
		final String arcroleType = linkPrefix+"arcroleType";
		final String xlinkRole = xlinkPrefix+"role";
		final String xlinkArcrole = xlinkPrefix+"arcrole";
		final String xlinkHref  = xlinkPrefix+"href";
		
		for (int annotationCnt=0; annotationCnt < annotation.getLength(); annotationCnt++) {
			if (annotation.item(annotationCnt).getNodeType() == Node.TEXT_NODE) continue;
			
			if ((prefix + "appinfo").equals(annotation.item(annotationCnt).getNodeName())) {
				
				final NodeList appinfo = annotation.item(annotationCnt).getChildNodes();

				for (int appinfoCnt=0; appinfoCnt<appinfo.getLength(); appinfoCnt++ ) {
					if (appinfo.item(appinfoCnt).getNodeType() == Node.TEXT_NODE) continue;
					if (appinfo.item(appinfoCnt).getNodeType() == Node.COMMENT_NODE) continue;
					
					final Node n = appinfo.item(appinfoCnt);
					
					// обрабатываем <link:linkbaseRef xlink:type="simple" xlink:href="SR_0420409-presentation.xml" xlink:role="http://www.xbrl.org/2003/role/presentationLinkbaseRef" xlink:arcrole="http://www.w3.org/1999/xlink/properties/linkbase"/>
					if (linkbaseRef.equals(n.getNodeName())) {
						
						final Node arcrole = n.getAttributes().getNamedItem(xlinkArcrole);
						final Node role = n.getAttributes().getNamedItem(xlinkRole);
						final Node href = n.getAttributes().getNamedItem(xlinkHref);
						
						// пока не обрабатываем файлы без заданной роли
						// TODO: обработка файлов с формулами
						if ((arcrole!=null) && (role!=null) && (href!=null) && "http://www.w3.org/1999/xlink/properties/linkbase".equals(arcrole.getTextContent())) {

							// сразу преобразуем относительный путь файла в архиве в в абсолютный
							if ("http://www.xbrl.org/2003/role/definitionLinkbaseRef".equals(role.getTextContent())) {
								definition.add(Catalog.changePath(location, href.getTextContent()));
							} else if ("http://www.xbrl.org/2003/role/presentationLinkbaseRef".equals(role.getTextContent())) {
								presentation.add(Catalog.changePath(location, href.getTextContent()));
							} else if ("http://www.xbrl.org/2003/role/labelLinkbaseRef".equals(role.getTextContent())) {
								label.add( Catalog.changePath(location, href.getTextContent()));
							} else if ("http://www.xbrl.org/2003/role/referenceLinkbaseRef".equals(role.getTextContent())) {
								reference.add(Catalog.changePath(location, href.getTextContent()));
							} else {
								System.out.println("    Ошибка! Неизвестный тип role " + location + " " + role.getTextContent());
							}
						}
					} else if (roleType.equals(n.getNodeName())) {
						
						this.roleType.add(new XsdRole(n, linkPrefix));
						
					} else if (arcroleType.equals(n.getNodeName())) {
						
						// пропускаем
						
					} else {
						System.out.println("    Ошибка! Неизвестный тип roletype " + location + " " + n.getNodeName() + " " + n.getTextContent());
					}
				}
				
				if (presentation==null && definition==null && label==null && reference==null) {
					System.out.println("    Ошибка! Не найдены файлы definition или presentation в " + location);
				}

				break;
			}
			
		}
		
		
	}

	
	
	/**
	 * Обрабатываем строки import, чтобы получить ссылки на используемые справочники и стандартные правила xbrl 
	 * @param node
	 */
	private void parseImport(Node node) {

		final NamedNodeMap elAttr = node.getAttributes();
		String namespace = null;
		String schemaLocation = null;
		
		for (int a = 0; a < elAttr.getLength(); a++) {
			final String itemName = elAttr.item(a).getNodeName();
			final String text = elAttr.item(a).getTextContent();

			switch (itemName) {
			case "namespace":
				namespace = text;
				break;
			case "schemaLocation":
				schemaLocation = text;
				break;
			default:
				System.out.println("    Ошибка! " + location + " неизвестный атрибут " + itemName + " у " + node.getNodeName()); 
				break;
			}
		}
		
		if (namespace != null && schemaLocation != null) {
			// сразу переводим относительные пути в абсолютные
			
			final ImportElement element = new ImportElement(namespace, 
					Catalog.changePath(location, schemaLocation));
			
			//System.out.println(Catalog.changePath(location, schemaLocation));

			// добавляем ссылку на справочники ЦБ и ifrs
			if (element.namespace.toLowerCase().startsWith("http://cbr.ru/") 
					|| element.namespace.toLowerCase().startsWith("http://www.cbr.ru/")
					|| element.namespace.toLowerCase().startsWith("http://xbrl.ifrs.org/")
					|| element.namespace.toLowerCase().startsWith("http://www.eurofiling.info/")
					) {

				importElements.add(element);

			// не добавляем ссылку на параметры
			} else if (element.namespace.toLowerCase().equals("http://parameters")) { 
				
			// не добавляем стандартные элементы 
			} else if (element.namespace.toLowerCase().startsWith("http://xbrl.org/") 
					//|| element.namespace.toLowerCase().startsWith("http://www.eurofiling.info/")
					|| element.namespace.toLowerCase().startsWith("http://www.xbrl.org/")
					//|| element.namespace.toLowerCase().startsWith("http://xbrl.ifrs.org/")
					) {

			}  else {
				System.out.println("    Ошибка! " + location + " Неизвестные атрибуты " + element.namespace + " -> " + element.schemaLocation);
			}
			
		} else {
			System.out.println("    Ошибка! " + location + " пустые атрибуты у " + node.getNodeName() + " " + namespace + " " + schemaLocation);
		}

		
	}

	
	
	/**
	 * Обрабатываем xsd:element, готовый элемент добавляем в elements
	 * @param node
	 */
	private void parseElement(Node node) {
//  <xsd:element name="TechnicalTable" id="ifrs-ru_TechnicalTable" type="xbrli:stringItemType" model:fromDate="2018-01-01" substitutionGroup="xbrldt:hypercubeItem" model:creationDate="2018-01-01" abstract="true" nillable="true" xbrli:periodType="duration"/>

		final XbrlElement xbrl = new XbrlElement();
		
		xbrl.parent = targetNamespace;
		
		final NamedNodeMap elAttr = node.getAttributes();
		
		for (int a = 0; a < elAttr.getLength(); a++) {
			final String itemName = getFullUri(elAttr.item(a).getNodeName());
			final String text = elAttr.item(a).getTextContent();

			switch (itemName) {
			case "name":
				xbrl.name = text;
				break;
			case "id":
				xbrl.id = text;
				break;
			case "substitutionGroup":
				xbrl.substitutionGroup = XbrlSubstitutionGroup.getItemByUri(getFullUri(text));
				if (xbrl.substitutionGroup==null) System.out.println("    Ошибка! Неизвестный тип substitutionGroup - " + getFullUri(text));
				break;
			case "type":
				xbrl.type = XbrlType.getItemByUri(getFullUri(text));
				if (xbrl.type==null) System.out.println("    Ошибка! Неизвестный тип type - " + getFullUri(text));
				break;
			case "abstract":
				if ("true".equals(text)) {
					xbrl.abstractElement = true;
				}
				break;
			case "nillable":
				if ("true".equals(text)) {
					xbrl.nillable = true;
				}
				break;
			case "{http://www.xbrl.org/2003/instance}periodType":
				xbrl.periodType = XbrlPeriodType.getItemByUri(getFullUri(text));
				if (xbrl.periodType==null) System.out.println("    Ошибка! Неизвестный тип periodType - " + getFullUri(text));
				break;
			case "{http://www.eurofiling.info/xbrl/ext/model}creationDate": // даты игнорируем
			case "{http://www.eurofiling.info/xbrl/ext/model}fromDate":
			case "{http://www.eurofiling.info/xbrl/ext/model}toDate":
				break;
			case "{http://www.xbrl.org/2003/instance}balance":
				xbrl.balance = XbrlTypeBalance.getItemByUri(getFullUri(text));
				if (xbrl.balance==null) System.out.println("    Ошибка! Неизвестный тип balance - " + getFullUri(text));
				break;
			case "{http://xbrl.org/2014/extensible-enumerations}domain":
				xbrl.domain = getFullUri(text);
				break;
			case "{http://xbrl.org/2014/extensible-enumerations}linkrole":
				xbrl.linkrole = getFullUri(text);
				//System.out.println(getFullUri(text));
				break;
			case "{http://xbrl.org/2005/xbrldt}typedDomainRef":
				xbrl.typedDomain = targetNamespace + text;
				//System.out.println(targetNamespace + " - " + text);
				break;
			default:
				System.out.println("    Ошибка! " + location + " неизвестный атрибут " + itemName + " у " + node.getNodeName());
			}
		}
		
		
		// TODO: обработка дочерних элементов - restriction
		
		// если есть дочерние элементы, то ищем правила проверки
		if (node.hasChildNodes()) {
			XsdRestriction.parse(node.getChildNodes(), xbrl, ns);
		}
		
		
		
		elements.add(xbrl);
	}
	
	
	private String getFullUri(String shortName) {
		final int pos = shortName.indexOf(":");
		String out = shortName;
		
		// если есть префикс, по нему получаем полное наименование атрибута
		if (pos>=0) {
			final String prefix = shortName.substring(0,pos).intern();
			if (ns.containsKey(prefix)) {
				out = "{" + ns.get(prefix) + "}"+shortName.substring(pos + 1).intern();
			}
		}
		return out;
	}
	

	// получить ссылку на исходный файл в архиве
	public String getLocation() {
		return location;
	}
	
	
}
