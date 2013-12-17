/**
 * 
 */
package org.aksw.assessment.question.informativeness;

import com.hp.hpl.jena.graph.Triple;

/**
 * @author Lorenz Buehmann
 *
 */
public interface InformativenessGenerator {

	double computeInformativeness(Triple triple);
}
