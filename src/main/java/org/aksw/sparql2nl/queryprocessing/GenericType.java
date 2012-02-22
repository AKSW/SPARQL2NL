package org.aksw.sparql2nl.queryprocessing;

public enum GenericType {
	ENTITY("entity"),
	VALUE("value"),
	RELATION("property"),
	TYPE("type"),
	UNKNOWN("UltimativeGenericEntity");

	private final String nlr;

	GenericType(String nlr) {
		this.nlr = nlr;
	}

	public String getNlr() {
		return nlr;
	}
}
