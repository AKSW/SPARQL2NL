/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.dataset;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import uk.ac.shef.wit.simmetrics.similaritymetrics.QGramsDistance;

/**
 *
 * @author ngonga
 */
public class PropertySimilarityCorrelation {

    public static Map<Set<ObjectProperty>, Double> getCooccurrences(NamedClass cls, Set<ObjectProperty> properties) {
        return getCooccurrences(cls, properties, 0d);
    }

    public static Map<Set<ObjectProperty>, Double> getCooccurrences(NamedClass cls, Set<ObjectProperty> properties, double threshold) {
        Map<Set<ObjectProperty>, Double> pair2similarity = new HashMap<Set<ObjectProperty>, Double>();
        //compute the frequency for each pair
        ResultSet rs;
        QGramsDistance qgrams = new QGramsDistance();
        for (ObjectProperty prop1 : properties) {
            for (ObjectProperty prop2 : properties) {
                Set<ObjectProperty> pair = Sets.newHashSet(prop1, prop2);
                if (!pair2similarity.containsKey(pair) && !prop1.equals(prop2)) {
                    double similarity = qgrams.getSimilarity(prop1.getName().substring(prop1.getName().lastIndexOf("/") + 1), prop2.getName().substring(prop2.getName().lastIndexOf("/") + 1));
                    if (similarity >= threshold) {
                        pair2similarity.put(pair, similarity);
                    }
                }
            }
        }
        return pair2similarity;
    }
}
