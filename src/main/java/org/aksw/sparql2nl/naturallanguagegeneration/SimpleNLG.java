/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aksw.sparql2nl.queryprocessing.TypeExtractor;
import simplenlg.features.Feature;
import simplenlg.features.Form;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author ngonga
 */
public class SimpleNLG extends GenericNLG {

    public SimpleNLG() {
        lexicon = Lexicon.getDefaultLexicon();
        nlgFactory = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);
    }

    /** Converts the representation of the query as Natural Language Element into
     * free text.
     * @param query Input query
     * @return Text representation
     */
    @Override
    public String getNLR(Query query) {
        return realiser.realiseSentence(convert2NLE(query));
    }

    /** Generates a natural language representation for a query
     * 
     * @param query Input query
     * @return Natural Language Representation
     */
    @Override
    public DocumentElement convert2NLE(Query query) {
        if (query.isSelectType()) {
            return convertSelect(query);
        } else if (query.isAskType()) {
            return convertAsk(query);
        } else if (query.isDescribeType()) {
            return convertDescribe(query);
        } else {
            SPhraseSpec head = nlgFactory.createClause();
            head.setSubject("This framework");
            head.setVerb("support");
            head.setObject("the input query");
            head.setFeature(Feature.NEGATED, true);
            DocumentElement sentence = nlgFactory.createSentence(head);
            DocumentElement doc = nlgFactory.createParagraph(Arrays.asList(sentence));
            return doc;
        }
    }

    /** Generates a natural language representation for SELECT queries
     * 
     * @param query Input query
     * @return Natural Language Representation
     */
    public DocumentElement convertSelect(Query query) {
        // List of sentences for the output
        List<DocumentElement> sentences = new ArrayList<DocumentElement>();
        
        // preprocess the query to get the relevant types
        TypeExtractor tEx = new TypeExtractor();
        Map<String, Set<String>> typeMap = tEx.extractTypes(query);

        // contains the beginning of the query, e.g., "this query returns"
        SPhraseSpec head = nlgFactory.createClause();
        String conjunction = "such that";
        NLGElement body;
        NLGElement postConditions;

        //process head
        //we could create a lexicon from which we could read these
        head.setSubject("This query");
        head.setVerb("retrieve");
        head.setObject(processTypes(typeMap));
        //now generate body
        body = getNLFromElements(getWhereElements(query));
        //now add conjunction
        CoordinatedPhraseElement phrase1 = nlgFactory.createCoordinatedPhrase(head, body);
        phrase1.setConjunction("such that");
        // add as first sentence
        sentences.add(nlgFactory.createSentence(phrase1));
        //this concludes the first sentence. 
        
        // The second sentence deals with the optional clause (if it exists)
        List<Element> optionalElements = getOptionalElements(query);
        if (optionalElements != null) {
            NLGElement optional = getNLFromElements(optionalElements);
            optional.setFeature(Feature.CUE_PHRASE, "Optionally,");
            sentences.add(nlgFactory.createSentence(optional));            
        }
        
        //The last sentence deals with the result modifiers
        DocumentElement result = nlgFactory.createParagraph(sentences);
        return result;
    }

    /** Fetches all elements of the query body, i.e., of the WHERE clause of a 
     * query
     * @param query Input query
     * @return List of elements from the WHERE clause
     */
    private static List<Element> getWhereElements(Query query) {
        List<Element> result = new ArrayList<Element>();
        ElementGroup elt = (ElementGroup) query.getQueryPattern();
        for (int i = 0; i < elt.getElements().size(); i++) {
            if (!elt.getElements().get(i).getClass().equals(ElementOptional.class)) {
                result.add(elt.getElements().get(i));
            }
        }
        return result;
    }

    /** Fetches all elements of the optional, i.e., of the OPTIONAL clause. 
     * query
     * @param query Input query
     * @return List of elements from the OPTIONAL clause if there is one, else null
     */
    private static List<Element> getOptionalElements(Query query) {
        ElementGroup elt = (ElementGroup) query.getQueryPattern();
        for (int i = 0; i < elt.getElements().size(); i++) {
            if (elt.getElements().get(i).getClass().equals(ElementOptional.class)) {
                return ((ElementGroup) ((ElementOptional) elt.getElements().get(i)).getOptionalElement()).getElements();
            }
        }
        return null;
    }

    private NLGElement processTypes(Map<String, Set<String>> typeMap) {
        List<NPPhraseSpec> objects = new ArrayList<NPPhraseSpec>();
        //process the type information to create the object(s)    
        for (String s : typeMap.keySet()) {
            // contains the objects to the sentence
            NPPhraseSpec object = nlgFactory.createNounPhrase(s);
            Set<String> types = typeMap.get(s);
            for (String type : types) {
                object.addPreModifier(getNPPhrase(type, true));
            }
            object.setFeature(Feature.CONJUNCTION, "or");
            objects.add(object);
        }
        if (objects.size() == 1) {
            return objects.get(0);
        } else {
            CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(objects.get(0), objects.get(1));
            if (objects.size() > 2) {
                for (int i = 2; i < objects.size(); i++) {
                    cpe.addCoordinate(objects.get(i));
                }
            }
            return cpe;
        }
    }

    public DocumentElement convertAsk(Query query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public DocumentElement convertDescribe(Query query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
