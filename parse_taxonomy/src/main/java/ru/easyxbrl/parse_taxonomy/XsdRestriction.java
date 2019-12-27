package ru.easyxbrl.parse_taxonomy;

import java.util.Map;
import java.util.regex.Pattern;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ru.easyxbrl.core.XbrlElement;
import ru.easyxbrl.core.XbrlRestriction;
import ru.easyxbrl.define.XbrlType;

public class XsdRestriction {

/*
    <xsd:simpleType>
      <xsd:restriction base="xsd:string">
        <xsd:minLength value="1"/>
      </xsd:restriction>
    </xsd:simpleType>

    <xsd:simpleType>
      <xsd:restriction base="xsd:string">
        <xsd:pattern value="[1-6][0-8][1-3][1-8][0-4][0-2]"/>
      </xsd:restriction>
    </xsd:simpleType>

    <xsd:complexType>
      <xsd:simpleContent>
        <xsd:restriction base="xbrli:dateItemType">
          <xsd:pattern value="[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]"/>
          <xsd:attributeGroup ref="xbrli:nonNumericItemAttrs"/>
        </xsd:restriction>
      </xsd:simpleContent>
    </xsd:complexType>

    <xsd:complexType>
      <xsd:simpleContent>
        <xsd:restriction base="xbrli:dateItemType">
          <xsd:pattern value="(19|20)\d\d-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])"/>
          <xsd:attributeGroup ref="xbrli:nonNumericItemAttrs"/>
        </xsd:restriction>
      </xsd:simpleContent>
    </xsd:complexType>

*/

	/**
	 * Просматриваем подчинённые элементы, ищем правила проверки и добавляем в список переданному элементу
	 * потокобезопасный статический метод 
	 * @param nodelist
	 * @param xbrl
	 */
	static public synchronized void parse(NodeList nodelist, XbrlElement xbrl, Map<String, String> ns) {
		for (int i=0; i < nodelist.getLength(); i++) {

			final Node node = nodelist.item(i);
			if ((node.getNodeType() == Node.TEXT_NODE) || (node.getNodeType() == Node.COMMENT_NODE)) continue;
			
			final String nodeName = node.getNodeName();
			final NodeList nl = node.getChildNodes();
			
			if (nodeName.endsWith("simpleType")) {
				for (int j=0; j < nl.getLength(); j++) {
					if (nl.item(j).getNodeType() == Node.TEXT_NODE || nl.item(j).getNodeType() == Node.COMMENT_NODE) continue;
					
					retriction(nl.item(j), xbrl, ns);
				}
			} else if (nodeName.endsWith("complexType")) {
				for (int j=0; j < nl.getLength(); j++) {
					if (nl.item(j).getNodeType() == Node.TEXT_NODE || nl.item(j).getNodeType() == Node.COMMENT_NODE) continue;
					
					if (nl.item(j).getNodeName().endsWith("simpleContent")) {
						NodeList n = nl.item(j).getChildNodes();
						for (int k = 0; k < n.getLength(); k++) {
							if (n.item(k).getNodeType() == Node.TEXT_NODE || n.item(k).getNodeType() == Node.COMMENT_NODE) continue;

							retriction(n.item(k), xbrl, ns);
						}
					} else if (nl.item(j).getNodeName().endsWith("complexContent")) {
						// TODO:  обработка сложного контента для условий
						
					} else {
						System.out.println(xbrl.parent + " " + xbrl.id + "Неизвестный тип " + nodeName + " " + nl.item(j).getNodeName());
					}
				}
			} else if (nodeName.endsWith("annotation")) {
				// TODO: обработка аннотаций
			} else {
				System.out.println(xbrl.parent + " " + xbrl.id + "Неизвестный тип " + nodeName);
			}
		}
	}
	
	static private void retriction(Node node, XbrlElement xbrl, Map<String, String> ns) {
	/**
        <xsd:restriction base="xbrli:dateItemType">
          <xsd:pattern value="[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]"/>
          <xsd:attributeGroup ref="xbrli:nonNumericItemAttrs"/>
          <xsd:minLength value="1"/>
        </xsd:restriction>
	 */
		if (node.getNodeName().endsWith("restriction")) {
			
			final XbrlRestriction r = new XbrlRestriction();
			
			final String[] base = node.getAttributes().getNamedItem("base").getTextContent().split(":");
			
			r.base = XbrlType.getItemByUri(base.length==1 ? base[0] : ("{" + ns.get(base[0]) + "}"+base[1]));
			if (r.base==null) System.out.println(xbrl.parent + " " + xbrl.id + "Неизвестный тип " + node.getAttributes().getNamedItem("base").getTextContent());
			
			if (xbrl.type == null) xbrl.type = r.base;
			
			final NodeList nl = node.getChildNodes();
			for (int i=0; i<nl.getLength(); i++) {
				final Node n = nl.item(i);
				if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.COMMENT_NODE) continue;

				final String nodeName = n.getNodeName();
				final Node value = n.getAttributes().getNamedItem("value");
				
				if (nodeName.endsWith("pattern")) {
					r.pattern = Pattern.compile(value.getTextContent());
				} else if (nodeName.endsWith("minLength")) {
					r.minLength = Integer.valueOf(value.getTextContent());
				} else if (nodeName.endsWith("maxLength")) {
					r.maxLength = Integer.valueOf(value.getTextContent());
				} else if (nodeName.endsWith("attributeGroup")) {
					// пропускаем
				} else {
					System.out.println(xbrl.parent + " " + xbrl.id + " Неизвестный тип " + nodeName);
				}
				
			}

			xbrl.restrictions.add(r);
			
		} else {
			System.out.println("Неизвестный тип " + node.getNodeName());
		}
		
		
	}
	
}
