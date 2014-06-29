/**
 *
 */
package org.aksw.sparql2nl.entitysummarizer.gender;


/**
 * Detects the gender of a given name.
 * @author Lorenz Buehmann
 *
 */
public interface GenderDetector {
	
	/**
	 * Returns the gender of the given name.
	 * @param name the name
	 * @return the gender
	 */
    Gender getGender(String name);
}
