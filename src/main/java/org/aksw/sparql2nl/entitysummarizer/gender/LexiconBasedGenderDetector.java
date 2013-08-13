/**
 *
 */
package org.aksw.sparql2nl.entitysummarizer.gender;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * @author Lorenz Buehmann
 *
 */
public class LexiconBasedGenderDetector implements GenderDetector {

    private Set<String> male;
    private Set<String> female;

    public LexiconBasedGenderDetector(Set<String> male, Set<String> female) {
        this.male = male;
        this.female = female;
    }

    /*
     * (non-Javadoc) @see
     * org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector#getGender(java.lang.String)
     */
    @Override
    public Gender getGender(String name) {
        if (male.contains(name)) {
            return Gender.MALE;
        } else if (female.contains(name)) {
            return Gender.FEMALE;
        } else {
            return Gender.UNKNOWN;
        }
    }

    public LexiconBasedGenderDetector() {
        try {
            male = new HashSet<String>();
            female = new HashSet<String>();
            List<String> lines = Files.readLines(new File("src/main/resources/male.txt"), Charsets.UTF_8);
            for (String l : lines) {
                l = l.trim();
                if (!l.startsWith("#") && !l.isEmpty()) {
                    male.add(l);
                }
            }
            lines = Files.readLines(new File("src/main/resources/female.txt"), Charsets.UTF_8);
            for (String l : lines) {
                l = l.trim();
                if (!l.startsWith("#") && !l.isEmpty()) {
                    female.add(l);
                }
            }
            
        } catch (Exception e) {
            
        }
    }

    public static void main(String[] args) throws Exception {
        Set<String> male = new HashSet<String>();
        Set<String> female = new HashSet<String>();
        List<String> lines = Files.readLines(new File("src/main/resources/male.txt"), Charsets.UTF_8);
        for (String l : lines) {
            l = l.trim();
            if (!l.startsWith("#") && !l.isEmpty()) {
                male.add(l);
            }
        }
        lines = Files.readLines(new File("src/main/resources/female.txt"), Charsets.UTF_8);
        for (String l : lines) {
            l = l.trim();
            if (!l.startsWith("#") && !l.isEmpty()) {
                female.add(l);
            }
        }
        LexiconBasedGenderDetector genderDetector = new LexiconBasedGenderDetector(male, female);
        System.out.println(genderDetector.getGender("Axel"));
    }
}
