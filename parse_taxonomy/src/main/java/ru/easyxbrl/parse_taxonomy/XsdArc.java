package ru.easyxbrl.parse_taxonomy;

import java.io.Serializable;

import ru.easyxbrl.core.Core;
import ru.easyxbrl.define.XbrlArcrole;

public class XsdArc implements Serializable {

	private static final long serialVersionUID = -3242342035817156909L;

	public XbrlArcrole arcrole = null;
	public String from = null;
	public String to = null;
	public String order = null;
	public String targetRole = null;
	public String preferredLabel = Core.labelRole;
	
}
