package org.aksw.sparql2nl.queryprocessing;

public enum GenericType {
	
	ENTITY("entity"),
	VALUE("value"),
	UNKNOWN("UltimativeGenericEntity");
	
	private final String nlr;
	
	GenericType(String nlr) {
		this.nlr = nlr;
	}
	
	public String getNlr() {
		return nlr;
	}

}
