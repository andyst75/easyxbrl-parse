package ru.easyxbrl.parse_taxonomy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.DimensionDefault;
import ru.easyxbrl.core.XbrlDefElement;
import ru.easyxbrl.core.XbrlDefinition;
import ru.easyxbrl.define.XbrlArcrole;

public class XsdSchemaDefinition {

	private String location = null;            // путь к файлу
	
	// таблица соответствий префикс - схема
	private final Map<String, String> ns = new HashMap<>(); // xmlns:purcb-dic="http://www.cbr.ru/xbrl/nso/purcb/dic/purcb-dic"
	private String linkPrefix = "";                         // если попадается ссылка на http://www.xbrl.org/2003/linkbase, то запоминаем префикс отдельно
	private String xlinkPrefix = "";                        // если попадается ссылка на http://www.w3.org/1999/xlink, то запоминаем префикс отдельно
	private String xbrldtPrefix = "";					   // если попадается ссылка на http://xbrl.org/2005/xbrldt, то запоминаем префикс отдельно
	
	// список требуемых справочников таксономии
	private final List<ImportElement> roleRefs = new ArrayList<>(); // элементы import namespace="http://www.cbr.ru/xbrl/udr/dim/dim-int" schemaLocation="../../../../../../udr/dim/dim-int.xsd"

	private Core core = null;
	private Map<String, Dictionary> dicts = null;

	public List<XbrlDefinition> defList = new ArrayList<>();
	
	
	/*
  <link:definitionLink xlink:type="extended" xlink:role="http://www.cbr.ru/xbrl/nso/dic/purcb-dic/OKSMList">
    <link:loc xlink:type="locator" xlink:href="purcb-dic.xsd#purcb-dic_OKSMList" xlink:label="OKSMList"/>
    <link:loc xlink:type="locator" xlink:href="../../../udr/dom/mem-int.xsd#mem-int_Strana_004AfgAfganistanMember" xlink:label="Strana_004AfgAfganistanMember"/>
    <link:definitionArc xlink:type="arc" xlink:arcrole="http://xbrl.org/int/dim/arcrole/domain-member" xlink:from="OKSMList" xlink:to="Strana_004AfgAfganistanMember" use="optional" priority="0" order="1.0"/>
    <link:loc xlink:type="locator" xlink:href="../../../udr/dom/mem-int.xsd#mem-int_Strana_008AlbAlbaniyaMember" xlink:label="Strana_008AlbAlbaniyaMember"/>
    <link:definitionArc xlink:type="arc" xlink:arcrole="http://xbrl.org/int/dim/arcrole/domain-member" xlink:from="OKSMList" xlink:to="Strana_008AlbAlbaniyaMember" use="optional" priority="0" order="2.0"/>
    <link:definitionArc xlink:type="arc" xlink:arcrole="http://xbrl.org/int/dim/arcrole/domain-member" xlink:from="OKSMList" xlink:to="Liczo_bez_grazhdanstvaMember" xlink:title="definition: OKSMList to Liczo_bez_grazhdanstvaMember" order="355.0"/>
  </link:definitionLink>
  <link:definitionLink xlink:type="extended" xlink:role="http://www.cbr.ru/xbrl/nso/dic/purcb-dic/ValyutaList">
    <link:loc xlink:type="locator" xlink:href="purcb-dic.xsd#purcb-dic_ValyutaList" xlink:label="ValyutaList"/>
    <link:loc xlink:type="locator" xlink:href="../../../udr/dom/mem-int.xsd#mem-int_Valyuta_008AllLekMember" xlink:label="Valyuta_008AllLekMember"/>
    <link:definitionArc xlink:type="arc" xlink:arcrole="http://xbrl.org/int/dim/arcrole/domain-member" xlink:from="ValyutaList" xlink:to="Valyuta_008AllLekMember" use="optional" priority="0" order="1.0"/>
    <link:loc xlink:type="locator" xlink:href="../../../udr/dom/mem-int.xsd#mem-int_Valyuta_048BhdBaxrejnskijDinarMember" xlink:label="Valyuta_048BhdBaxrejnskijDinarMember"/>
    <link:definitionArc xlink:type="arc" xlink:arcrole="http://xbrl.org/int/dim/arcrole/domain-member" xlink:from="ValyutaList" xlink:to="Valyuta_048BhdBaxrejnskijDinarMember" use="optional" priority="0" order="6.0"/>
....
	 */
	
	
	
	String sectionName = null;
	String arcName = null;
	
	DimensionDefault dimDefault = null;

	XsdSchemaDefinition(Core core, String location, Map<String, Dictionary> dicts, String sectionName, String arcName, DimensionDefault dimDefault) throws Exception {
		if (location==null) return;
		this.location = (location.startsWith("http:") || location.startsWith("https:")) ? LoadTaxonomyFromZip.catalog.uriToPath(location) : location;
		this.core = core;
		this.dicts = dicts;
		this.sectionName = sectionName;
		this.arcName = arcName;
		this.dimDefault = dimDefault;

		
		try {
			
			ByteArrayInputStream ba = new ByteArrayInputStream(LoadTaxonomyFromZip.zipFiles.get(LoadTaxonomyFromZip.zipFilenames.get(this.location)));
			
			final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ba);
			
			final String prefix = LoadTaxonomyFromZip.getSchemaPrefix(LoadTaxonomyFromZip.XSD_LABEL_ROOT, document);
			final String linkRoleRef = prefix + "roleRef";
			final String linkDefinitionLink = prefix + sectionName; // "definitionLink"
			
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
					
					if ((el.getNodeType() == Node.TEXT_NODE) || 
							(el.getNodeType() == Node.COMMENT_NODE)) continue;
					
					// встречается чаще, поэтому первым сравнением
					if (linkDefinitionLink.equals(el.getNodeName())) {
						
						// обработка конкретной секции
						parseDefinitionLink(el, arcName);
						
					} else if (linkRoleRef.equals(el.getNodeName())) {
						
						parseRoleRef(el);
						
					} 
				}
			}
				

		} catch (Exception e) {
			//throw new RuntimeException("Ошибка обработки " + location);
			e.printStackTrace();
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
					if ("http://xbrl.org/2005/xbrldt".equals(text)) xbrldtPrefix = itemName.substring(6) + ":";
					
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
				} else if ("xmlns".equals(itemName) && "http://www.xbrl.org/2003/linkbase".equals(text)){
					// не обрабатываем стандартное обозначение
				} else {
					System.out.println("    Ошибка! " + location + " неизвестный атрибут " + itemName + " - " + text); 
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
			final String text = elAttr.item(a).getTextContent();

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

	
	private final Map<String, XbrlDefElement> locMap = new LinkedHashMap<>(); // важна последовательность элементов, даже без order 
	private final List<XsdArc> definitionArc = new ArrayList<>(); // для элементов definitionArc

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
	private String linkDefinitionArc;
	
	private String xbrldtUsable;
	private String xbrldtContextElement;
	private String xbrldtClosed;
	private String xbrldtTargetRole;
	
	
	/**
	 * Обрабатываем xsd:element, готовый элемент добавляем в elements
	 * @param node
	 */
	private void parseDefinitionLink(Node node, String arcName) {
	
		List<XsdArc> definitionArc = this.definitionArc;
		
		locMap.clear();
		definitionArc.clear();
		
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
			linkDefinitionArc = linkPrefix + arcName; // "definitionArc"
			
			xbrldtUsable = xbrldtPrefix + "usable";
			xbrldtContextElement = xbrldtPrefix + "contextElement";
			xbrldtClosed = xbrldtPrefix + "closed";
			xbrldtTargetRole = xbrldtPrefix + "targetRole";

			
			String role = null;
			
			for (int i=0; i<nodeAttr.getLength(); i++) {
				if (xlinkType.equals(nodeAttr.item(i).getNodeName()) && xlinkTypeDef.equals(nodeAttr.item(i).getTextContent())) {
					// пропускаем
				} else if (xlinkRole.equals(nodeAttr.item(i).getNodeName())) {
					role = nodeAttr.item(i).getTextContent();
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
				
				if (linkDefinitionArc.equals(nodeName)) {
					
					parseDefinitionArc(nl.item(nlCnt));
					
				} else if (linkLoc.equals(nodeName)) {
					
					parseLoc(nl.item(nlCnt));
					
				} else {
					System.out.println("    Ошибка! " + location + " неизвестный элемент " + nodeName);
				}
			}
			
			//System.out.println("  " + location + " = " + locMap.size() + " " + definitionMap.size() + " " +definitionArc.size());
			
			// связываем элементы
			definitionArc.forEach( l -> {
				
				final XbrlDefElement elFrom = locMap.get(l.from);
				final XbrlDefElement elTo = locMap.get(l.to);

				elTo.arcrole = l.arcrole;
				elTo.order = l.order;
				elTo.targetRole = l.targetRole;
				elTo.preferredLabel = l.preferredLabel;
				elTo.label = l.to; // уникальное наименование элемента в секции

				if (l.arcrole.equals(XbrlArcrole.NOT_ALL)) {

					elFrom.notAll.add(elTo);
					
				} else if (l.arcrole.equals(XbrlArcrole.DEFAULT)) {
					
					dimDefault.putDefaults(elFrom.element, elTo.element);
					
				} else {
					
					elTo.parent = elFrom;
					elFrom.child.add(elTo);
					
				}
				
			});
			
			final XbrlDefinition def = new XbrlDefinition(role);
			
			// добавляем все корневые элементы, соблюдая порядок
			Iterator<XbrlDefElement> it = locMap.values().iterator();
			while (it.hasNext()) {
				final XbrlDefElement el = it.next();
				if (el.parent==null) {
					
					// элементы со связью NOT_All не включаем в общий список
					if (!XbrlArcrole.NOT_ALL.equals(el.arcrole)) {
						def.child.add(el);
					}
					
				}
			}

			defList.add(def);

		} else {
			System.out.println("    Ошибка! " + location + " у " + node.getNodeName() + " нет атрибутов");
		}
		
	}


	// разбираем link:definitionArc
	private void parseDefinitionArc(Node node) {
		final NamedNodeMap attr = node.getAttributes();
		
		final XsdArc el = new XsdArc();
		
		for (int i=0; i<attr.getLength();i++) {
			final String attrName = attr.item(i).getNodeName();
			final String attrValue = attr.item(i).getTextContent();
			
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
			} else if ("preferredLabel".equals(attrName)) {
				el.preferredLabel = attrValue;
			} else if ("use".equals(attrName)) {
				//el.title = attrValue;
			} else if ("order".equals(attrName)) {
				el.order = attrValue;
			} else if ("priority".equals(attrName)) {
				//el.title = attrValue;
			} else if (xbrldtUsable.equals(attrName)) {
				//el.title = attrValue;
			} else if (xbrldtContextElement.equals(attrName)) {
				//el.title = attrValue;
			} else if (xbrldtClosed.equals(attrName)) {
				//el.title = attrValue;
			} else if (xbrldtTargetRole.equals(attrName)) {
				el.targetRole = attrValue;
			} else {
				System.out.println("    Ошибка! " + location + ", definitionArc,  неизвестный элемент " + attrName + ", " + attrValue);
			}
		}
		
		
		definitionArc.add(el);
		
	}
	
	
	// разбираем link:loc
	private void parseLoc(Node node) {
		final NamedNodeMap attr = node.getAttributes();
		
		String label = null;
		XbrlDefElement el = new XbrlDefElement();
		
		for (int i=0; i<attr.getLength();i++) {
			final String attrName = attr.item(i).getNodeName();
			final String attrValue = attr.item(i).getTextContent();
			
			//System.out.println(attrValue+" -> "+attrName);
			
			if (xlinkType.equals(attrName) && "locator".equals(attrValue)) {
				// пропускаем
//			} else if (xlinkRole.equals(attrName)) {
				// пропускаем
//			} else if (xlinkType.equals(attrName)) {
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

				try { 
					
					el.element = core.dicts.getElement(dicts.get(elementPath[0]).targetNamespace, elementPath[1]);
					
				} catch (Exception ex) {
					
					System.out.println(location + " -> " + attrValue + " " + dicts.containsKey(elementPath[0]));
					
				}

				if (el.element==null) {
					
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
