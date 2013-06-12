/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.rules;

import java.util.List;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.SPhraseSpec;

/**
 *
 * @author ngonga
 */
public interface Rule {
    public int isApplicable(List<SPhraseSpec> phrases);
    public List<SPhraseSpec> apply(List<SPhraseSpec> phrases);
}
