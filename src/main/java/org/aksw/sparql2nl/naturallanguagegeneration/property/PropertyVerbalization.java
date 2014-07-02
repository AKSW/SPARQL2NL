/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration.property;

/**
 * @author Lorenz Buehmann
 *
 */
public class PropertyVerbalization {
	
	private PropertyVerbalizationType verbalizationType;
	private String propertyURI;
	private String propertyText;
	private String expandedVerbalization;
	
	
	public PropertyVerbalization(String propertyURI, String propertyText, PropertyVerbalizationType verbalizationType) {
		this.propertyURI = propertyURI;
		this.propertyText = propertyText;
		this.verbalizationType = verbalizationType;
		this.expandedVerbalization = propertyText;
	}
	
	/**
	 * @return the property URI
	 */
	public String getProperty() {
		return propertyURI;
	}
	
	/**
	 * @return the propertyText
	 */
	public String getVerbalizationText() {
		return propertyText;
	}
	
	/**
	 * @return the expanded verbalization text
	 */
	public String getExpandedVerbalizationText() {
		return expandedVerbalization;
	}
	
	/**
	 * 
	 * @param expandedVerbalization
	 */
	public void setExpandedVerbalizationText(String expandedVerbalization) {
		this.expandedVerbalization = expandedVerbalization;
	}
	
	public boolean isNounType(){
		return verbalizationType == PropertyVerbalizationType.NOUN;
	}
	
	public boolean isVerbType(){
		return verbalizationType == PropertyVerbalizationType.VERB;
	}
	
	public boolean isUnspecifiedType(){
		return !(isVerbType() || isNounType());
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "URI:" + propertyURI + "\nText: " + propertyText + "\nExpanded Text:" + expandedVerbalization + "\nType: " + verbalizationType;
	}

}
