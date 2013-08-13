/**
 *
 */
package org.aksw.sparql2nl.entitysummarizer.gender;

/**
 * @author Lorenz Buehmann
 *
 */
public interface GenderDetector {

    Gender getGender(String name);

    enum Gender {

        MALE, FEMALE, UNKNOWN;
    }
}
