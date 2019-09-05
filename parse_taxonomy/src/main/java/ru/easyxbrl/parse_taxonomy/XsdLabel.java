package ru.easyxbrl.parse_taxonomy;

import java.io.Serializable;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.core.XbrlLabel;

public class XsdLabel implements Serializable {

	private static final long serialVersionUID = -6123142035817156909L;

	public String label = null;
	public String role = null;
	public String title = null;
	public String id = null;
	public String lang = null;
	public String text = null;
	
	public XbrlLabel toXbrlLabel() {
		return new XbrlLabel((role==null || "".equals(role)) ? Core.labelRole : role,
				(lang==null || "".equals(lang)) ? Core.lang : lang,
						text==null?"":text);
	}
}
