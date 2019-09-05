package ru.easyxbrl.parse_taxonomy;

import java.io.Serializable;

public class ImportElement implements Serializable{
	private static final long serialVersionUID = 2501069596815772254L;
	
	public String namespace = null;
	public String schemaLocation = null;
	
	public ImportElement() { };
	
	public ImportElement(String namespace, String schemaLocation) {
		this.namespace = namespace;
		this.schemaLocation = schemaLocation;
	};
	
	
	@Override
	public boolean equals(Object obj){
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
	    if (!(obj instanceof ImportElement))return false;
	    final ImportElement o = (ImportElement)obj;
	    return ((namespace!=null && namespace.equals(o.namespace)) || (namespace==null && o.namespace==null))
	    		&& ((schemaLocation!=null && schemaLocation.equals(o.schemaLocation)) || (schemaLocation==null && o.schemaLocation==null));
	}	
	
	@Override
    public int hashCode() {
        return super.hashCode();
    }

}
