/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.gender;

enum Gender {
	MALE, FEMALE, UNKNOWN;
}

/**
 * @author Lorenz Buehmann
 *
 */
public interface GenderDetector {

	Gender getGender(String name);
}
