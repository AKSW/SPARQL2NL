/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import com.hp.hpl.jena.graph.Triple;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author ngonga
 */
public interface QuestionGenerator {
    Set<Question> getQuestions(Map<Triple, Double> informativenessMap, int difficulty, int number);
    
}
