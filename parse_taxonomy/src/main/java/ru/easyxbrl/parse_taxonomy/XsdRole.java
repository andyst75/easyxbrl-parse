package ru.easyxbrl.parse_taxonomy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XsdRole implements Serializable {

	private static final long serialVersionUID = 1328888361567173920L;
	
	
	public String roleURI = null;    // uri
	public String id = null;         // id в родительском uri
	public String definition = null; // описание секции
	
	public List<String> usedOn = new ArrayList<>();
	
	public XsdRole() {};
	
	public XsdRole(Node node, String linkPrefix) {
		
		if (node.hasAttributes()) {
			final Map<String, String> attr = new HashMap<>();
			
			for (int i=0; i<node.getAttributes().getLength(); i++) {
				attr.put(node.getAttributes().item(i).getNodeName(), node.getAttributes().item(i).getTextContent());
			}
			roleURI = attr.get("roleURI");
			id = attr.get("id");

			final NodeList nodes = node.getChildNodes();
			final String def  = linkPrefix + "definition";
			final String used = linkPrefix + "usedOn";
			
			for (int i=0; i < nodes.getLength(); i++) {
				if (nodes.item(i).getNodeType() == Node.TEXT_NODE) continue;
				
				final Node n = nodes.item(i);
				final String nodeName = n.getNodeName();
				final String nodeText = n.getTextContent();
				
				if (used.equals(nodeName)) {
					usedOn.add(nodeText);
				} else if (def.equals(nodeName)) {
					definition = nodeText;
				}
			}
			
			attr.clear();
		} else {
			System.out.println("    Ошибка! Нет атрибутов у roleType");
		}
		
	}
	
}
