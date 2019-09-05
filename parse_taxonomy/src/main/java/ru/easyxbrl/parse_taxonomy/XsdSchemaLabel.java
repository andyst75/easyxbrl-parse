package ru.easyxbrl.parse_taxonomy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.XbrlElement;
import ru.easyxbrl.core.XbrlLabel;
import ru.easyxbrl.define.XbrlArcrole;

/**
 * Обработку текстовых меток запускаем в нескольких потоках
 * @author Андрей
 *
 */
public class XsdSchemaLabel {

	
	private String location = null;            // путь к файлу
	
	// таблица соответствий префикс - схема
	public final Map<String, String> ns = new HashMap<>(); // xmlns:purcb-dic="http://www.cbr.ru/xbrl/nso/purcb/dic/purcb-dic"
	public String linkPrefix = "";                        // если попадается ссылка на http://www.xbrl.org/2003/linkbase, то запоминаем префикс отдельно
	public String xlinkPrefix = "";                       // если попадается ссылка на http://www.w3.org/1999/xlink, то запоминаем префикс отдельно
	
	// список требуемых справочников таксономии
	public final List<ImportElement> roleRefs = new ArrayList<>(); // элементы import namespace="http://www.cbr.ru/xbrl/udr/dim/dim-int" schemaLocation="../../../../../../udr/dim/dim-int.xsd"

	// список элементов справочника
	public final List<XbrlElement> elements = new ArrayList<>(); // элементы import namespace="http://www.cbr.ru/xbrl/udr/dim/dim-int" schemaLocation="../../../../../../udr/dim/dim-int.xsd"
	
	// список описания секций отчётности 
	public final List<XsdRole> roleType = new ArrayList<>();
	
	private Core core = null;
	//private String targetNamespace = null;
	Map<String, Dictionary> dicts = null;
	Map<XbrlElement, List<XbrlLabel>> localMap = null;
	
	public XsdSchemaLabel(Core core, String location, Map<String, Dictionary> dicts, Map<XbrlElement, List<XbrlLabel>> localMap) throws Exception {
		if (location==null) return;
		this.location = (location.startsWith("http:") || location.startsWith("https:")) ? LoadTaxonomyFromZip.catalog.uriToPath(location) : location;
		this.core = core;
		this.dicts = dicts;
		if (localMap!=null) this.localMap = Collections.synchronizedMap(localMap);
		
		
		try {
			final ByteArrayInputStream ba = new ByteArrayInputStream(LoadTaxonomyFromZip.zipFiles.get(LoadTaxonomyFromZip.zipFilenames.get(this.location)));
			
			final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ba);
			
			final String prefix = LoadTaxonomyFromZip.getSchemaPrefix(LoadTaxonomyFromZip.XSD_LABEL_ROOT, document);
			final String linkRoleRef = prefix + "roleRef";
			final String linkLabelLink = prefix + "labelLink";
			
			final NodeList schema = document.getElementsByTagName(prefix + LoadTaxonomyFromZip.XSD_LABEL_ROOT);
			
			
			// начинаем заполнять структуру точки входа
			if (schema != null && schema.getLength() > 0) {

				final Node node = schema.item(0);
				
				// обрабатываем заголовок
				parseHeader(node);
				
				// выбираем все подчинённые элементы
				// обрабатываем: link:roleRef, link:labelLink
				final NodeList importList = node.getChildNodes();

				for (int i = 0; i < importList.getLength(); i++) {
					
					final Node el = importList.item(i);
					
					if (el.getNodeType() == Node.TEXT_NODE) continue;
					
					// встречается чаще, поэтому первым сравнением
					if (linkLabelLink.equals(el.getNodeName())) {
						
						parseLabelLink(el);
						
					} else if (linkRoleRef.equals(el.getNodeName())) {
						
						parseRoleRef(el);
						
					} 
				}
			}
				

		} catch (Exception e) {
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
						throw new Exception("XsdSchemaLabel - ошибка в заголовочном файле, " + location);
					}
					
				} else if (
						("xmlns".equals(itemName) && "http://www.w3.org/2001/XMLSchema".equals(text))
								|| (itemName.endsWith("schemaLocation") && text.contains("linkbase"))){
					// не обрабатываем стандартное обозначение
				} else {
					System.out.println("    Ошибка!" + location + " неизвестный атрибут " + itemName); 
				}
			}
		} else {
			System.out.println(location + " атрибуты отсутствуют у " + LoadTaxonomyFromZip.XSD_SCHEMA_ROOT); 
		}
	}



	
	
	/**
	 * Обрабатываем строки roleRef, чтобы получить ссылки на roleURI, href 
	 * @param node
	 */
	private void parseRoleRef(Node node) {

		final NamedNodeMap elAttr = node.getAttributes();
		String namespace = null;
		String schemaLocation = null;
		
		for (int a = 0; a < elAttr.getLength(); a++) {
			final String itemName = elAttr.item(a).getNodeName();
			final String text = elAttr.item(a).getTextContent().strip().trim();

			if ("roleURI".equals(itemName)) {
				namespace = text;
			} else if ((xlinkPrefix + "href").equals(itemName)) {
				schemaLocation = text;
			} else if ((xlinkPrefix + "type").equals(itemName)) {
				// пропускаем элемент
			} else {
				System.out.println("    Ошибка! " + location + " неизвестный атрибут " + itemName + " у " + node.getNodeName()); 
			}
		}
		
		if (namespace != null && schemaLocation != null) {
			// сразу переводим относительные пути в абсолютные
			
			final ImportElement element = new ImportElement(namespace, 
					Catalog.changePath(location, schemaLocation));
			
			//System.out.println(Catalog.changePath(location, schemaLocation));

			// добавляем ссылку на справочники ЦБ
			if (element.namespace.toLowerCase().startsWith("http://cbr.ru/") 
					|| element.namespace.toLowerCase().startsWith("http://www.cbr.ru/")) {

				roleRefs.add(element);

			// не добавляем ссылку на параметры
			} else if (element.namespace.toLowerCase().equals("http://parameters")) { 
				
			// не добавляем стандартные элементы 
			} else if (element.namespace.toLowerCase().startsWith("http://xbrl.org/") 
					|| element.namespace.toLowerCase().startsWith("http://www.eurofiling.info/")
					|| element.namespace.toLowerCase().startsWith("http://www.xbrl.org/")
					|| element.namespace.toLowerCase().startsWith("http://xbrl.ifrs.org/")
					) {

			}  else {
				System.out.println("    Ошибка! " + location + " Неизвестные атрибуты " + element.namespace + " -> " + element.schemaLocation);
			}
			
		} else {
			System.out.println("    Ошибка! " + location + " пустые атрибуты у " + node.getNodeName() + " " + namespace + " " + schemaLocation);
		}

		
	}

	
	private final Map<String, XbrlElement> locMap = new HashMap<>(); // для элементов loc сразу получаем ссылку на элемент 
	private final Map<String, XsdLabel> labelMap = new HashMap<>(); // для элементов label
	private final List<XsdArc> labelArc = new ArrayList<>(); // для элементов labelArc

	private String xlinkType;
	private String xlinkRole;
	private String xlinkTypeDef;
	private String xlinkHref;
	private String xlinkLabel;
	private String xlinkTitle;
	private String xlinkArcrole;
	private String xlinkFrom;
	private String xlinkTo;

	private String linkLoc;
	private String linkLabel;
	private String linkLabelArc;
	
	
	/**
	 * Обрабатываем xsd:element, готовый элемент добавляем в elements
	 * @param node
	 */
	private void parseLabelLink(Node node) {
	/*
    <link:loc xlink:type="locator" xlink:href="mem-int.xsd#mem-int_EffektNaBalansAxis_NotApplicableMember" xlink:label="EffektNaBalansAxis_NotApplicableMember"/>
    <link:label xlink:type="resource" xlink:label="label_EffektNaBalansAxis_NotApplicableMember" xlink:role="http://www.xbrl.org/2003/role/label" xml:lang="en" id="label_EffektNaBalansAxis_NotApplicableMember">n/a [member]</link:label>
    <link:labelArc xlink:type="arc" xlink:arcrole="http://www.xbrl.org/2003/arcrole/concept-label" xlink:from="EffektNaBalansAxis_NotApplicableMember" xlink:to="label_EffektNaBalansAxis_NotApplicableMember" xlink:title="label: EffektNaBalansAxis_NotApplicableMember to label_EffektNaBalansAxis_NotApplicableMember"/>
    <link:label xlink:type="resource" xlink:label="label_EffektNaBalansAxis_NotApplicableMember_2" xlink:role="http://www.xbrl.org/2003/role/label" xml:lang="ru" id="label_EffektNaBalansAxis_NotApplicableMember_2">н/п </link:label>
    <link:labelArc xlink:type="arc" xlink:arcrole="http://www.xbrl.org/2003/arcrole/concept-label" xlink:from="EffektNaBalansAxis_NotApplicableMember" xlink:to="label_EffektNaBalansAxis_NotApplicableMember_2"/>
    <link:label xlink:type="resource" xlink:label="label_EffektNaBalansAxis_NotApplicableMember_3" xlink:role="http://www.cbr.ru/2018/role/TechnicalLabel" xml:lang="ru" id="label_EffektNaBalansAxis_NotApplicableMember_3">н/п [member]</link:label>
    <link:labelArc xlink:type="arc" xlink:arcrole="http://www.xbrl.org/2003/arcrole/concept-label" xlink:from="EffektNaBalansAxis_NotApplicableMember" xlink:to="label_EffektNaBalansAxis_NotApplicableMember_3"/>
	 
	 link:loc - базовый элемент
	  - из элемента href получаем uri справочника и id элемента
	  - запоминаем label - это ключ в локальном поиске
	 
	 link:label - язык и само значение метки, label - уникальный идентификатор метки, role - тип метки, lang - язык 
	 
	 link:labelArc - тип связи (concept-label), from - ссылка на базовый элемент, to = ссылка на конкретную метку (значение label)
	 */
	
		locMap.clear();
		labelMap.clear();
		labelArc.clear();
		
		if (node.hasAttributes()) {
			final NamedNodeMap nodeAttr = node.getAttributes();

			xlinkType = xlinkPrefix + "type";
			xlinkRole = xlinkPrefix + "role";
			xlinkArcrole = xlinkPrefix + "arcrole";
			xlinkFrom = xlinkPrefix + "from";
			xlinkTo = xlinkPrefix + "to";
			xlinkTypeDef = "extended";
			xlinkHref = xlinkPrefix + "href";
			xlinkLabel = xlinkPrefix + "label";
			xlinkTitle = xlinkPrefix + "title";

			linkLoc = linkPrefix + "loc";
			linkLabel = linkPrefix + "label";
			linkLabelArc = linkPrefix + "labelArc";
			
			for (int i=0; i<nodeAttr.getLength(); i++) {
				if (xlinkType.equals(nodeAttr.item(i).getNodeName()) && xlinkTypeDef.equals(nodeAttr.item(i).getTextContent())) {
					// пропускаем
				} else if (xlinkRole.equals(nodeAttr.item(i).getNodeName())) {
					// пропускаем
				} else {
					System.out.println("    Ошибка! " + location + " неизвестный атрибут " + nodeAttr.item(i).getNodeName());
					System.out.println("    xlinkType = " + xlinkType + ", xlinkRole = " + xlinkRole);
				}
			}
			
			final NodeList nl = node.getChildNodes();
			//System.out.println(node.getNodeName()+" "+node.getChildNodes().getLength());
			
			for (int nlCnt=0; nlCnt<nl.getLength(); nlCnt++) {
				if (nl.item(nlCnt).getNodeType() == Node.TEXT_NODE) continue;
				
				final String nodeName = nl.item(nlCnt).getNodeName();
				
				if (linkLabel.equals(nodeName)) {
					
					parseLabel(nl.item(nlCnt));
					
				} else if (linkLabelArc.equals(nodeName)) {
					
					parseLabelArc(nl.item(nlCnt));
					
				} else if (linkLoc.equals(nodeName)) {
					
					parseLoc(nl.item(nlCnt));
					
				} else {
					System.out.println("    Ошибка! " + location + " неизвестный элемент " + nodeName);
				}
			}
			
			// добавляем метки непосредственно элементу в ядре
			labelArc.parallelStream().forEach( l -> {
				final XbrlElement el = locMap.get(l.from);
				final XsdLabel label = labelMap.get(l.to);

				if (localMap==null) {
					el.label.add(label.toXbrlLabel());
				} else {
					localMap.putIfAbsent(el, new ArrayList<>());
					localMap.get(el).add(label.toXbrlLabel());
				}
			});
			
		} else {
			System.out.println("    Ошибка! " + location + " у " + node.getNodeName() + " нет атрибутов");
		}
		
	}


	// разбираем link:labelArc
	private void parseLabelArc(Node node) {
		final NamedNodeMap attr = node.getAttributes();
		
		XsdArc el = new XsdArc();
		
		for (int i=0; i<attr.getLength();i++) {
			final String attrName = attr.item(i).getNodeName();
			final String attrValue = attr.item(i).getTextContent().strip().trim();
			
			//System.out.println(attrValue+" -> "+attrName);
			
			if (xlinkType.equals(attrName) && "resource".equals(attrValue)) {
				// пропускаем
			} else if (xlinkArcrole.equals(attrName)) {
				el.arcrole = XbrlArcrole.getItemByUri(attrValue);
			} else if (xlinkFrom.equals(attrName)) {
				el.from = attrValue;
			} else if (xlinkTo.equals(attrName)) {
				el.to = attrValue;
			} else if (xlinkTitle.equals(attrName)) {
				//el.title = attrValue;
			} else if (xlinkType.equals(attrName)) {
				//el.title = attrValue;
			} else if ("use".equals(attrName)) {
				//el.title = attrValue;
			} else if ("order".equals(attrName)) {
				//el.title = attrValue;
			} else if ("priority".equals(attrName)) {
				//el.title = attrValue;
			} else {
				System.out.println("    Ошибка! " + location + ", labelArc,  неизвестный элемент " + attrName + ", " + attrValue);
			}
		}
		
		
		labelArc.add(el);
		
	}
	
	// разбираем link:label
	private void parseLabel(Node node) {
		final NamedNodeMap attr = node.getAttributes();
		
		XsdLabel el = new XsdLabel();
		
		el.text = node.getTextContent().strip();
		
		for (int i=0; i<attr.getLength();i++) {
			final String attrName = attr.item(i).getNodeName();
			final String attrValue = attr.item(i).getTextContent().trim().strip();
			
			//System.out.println(attrValue+" -> "+attrName);
			
			if (xlinkType.equals(attrName) && "resource".equals(attrValue)) {
				// пропускаем
			} else if (xlinkLabel.equals(attrName)) {
				el.label = attrValue;
			} else if (xlinkRole.equals(attrName)) {
				el.role = attrValue;
			} else if (xlinkTitle.equals(attrName)) {
				el.title = attrValue;
			} else if ("id".equals(attrName)) {
				el.id = attrValue;
			} else if ("xml:lang".equals(attrName)) {
				el.lang = attrValue;
			} else {
				System.out.println("    Ошибка! " + location + ", label,  неизвестный элемент " + attrName + ", " + attrValue);
			}
		}
		
		if (el.label!=null) {
			labelMap.put(el.label, el);
		} else {
			System.out.println("    Ошибка! " + location + ", label,  отсутствует метка " + el.text);
		}
		
	}
	
	
	// разбираем link:loc
	private void parseLoc(Node node) {
		final NamedNodeMap attr = node.getAttributes();
		
		String label = null;
		XbrlElement el = null;
		
		for (int i=0; i<attr.getLength();i++) {
			final String attrName = attr.item(i).getNodeName();
			final String attrValue = attr.item(i).getTextContent().strip().trim();
			
			//System.out.println(attrValue+" -> "+attrName);
			
			if (xlinkType.equals(attrName) && "locator".equals(attrValue)) {
				// пропускаем
			} else if (xlinkRole.equals(attrName)) {
				// пропускаем
			} else if (xlinkType.equals(attrName)) {
				// пропускаем
			} else if (xlinkTitle.equals(attrName)) {
				// пропускаем
			} else if (xlinkLabel.equals(attrName)) {
				label = attrValue;
			} else if (xlinkHref.equals(attrName)) {
				
				// 1. переводим относительные пути элементов в абсолютные внутри архива
				// 2. получаем путь до словаря внутри архива
				// 3. получаем targetNamespace словаря и к нему добавляем id элемента

				final String[] elementPath = Catalog.changePath(location, attrValue).split("#");
				
				//http://xbrl.ifrs.org/taxonomy/2015-03-11/full_ifrs/full_ifrs-cor_2015-03-11.xsd#ifrs-full_Assets
				//если путь начинается на http , то нужно преобразовать в путь в архиве
			
				if(elementPath[0].startsWith("http:")) {
					elementPath[0] = LoadTaxonomyFromZip.catalog.uriToPath(elementPath[0]);
				}
				el = core.dicts.getElement(dicts.get(elementPath[0]).targetNamespace, elementPath[1]);

				if (el==null) {
					System.out.println("    Ошибка! " + location + ", loc,  элемент не найден " + attrValue);
				}
			} else {
				System.out.println("    Ошибка! " + location + ", loc,  неизвестный элемент " + attrName + ", " + attrValue);
			}
		}
		if(label!=null && el!=null) {
			locMap.put(label, el);
		}
	}
	
	
	// получить ссылку на исходный файл в архиве
	public String getLocation() {
		return location;
	}
	
	
}
