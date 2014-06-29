/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationConfiguration {
	
	private double propertyFrequencyThreshold = 0.5;

	/**
	 * @return the propertyFrequencyThreshold
	 */
	public double getPropertyFrequencyThreshold() {
		return propertyFrequencyThreshold;
	}
	
	/**
	 * @param propertyFrequencyThreshold the propertyFrequencyThreshold to set
	 */
	public void setPropertyFrequencyThreshold(double propertyFrequencyThreshold) {
		this.propertyFrequencyThreshold = propertyFrequencyThreshold;
	}
}
