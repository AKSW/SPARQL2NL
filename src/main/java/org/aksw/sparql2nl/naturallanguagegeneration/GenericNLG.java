/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import simplenlg.features.Feature;
import simplenlg.features.Tense;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.framework.PhraseElement;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.sparql.expr.E_Regex;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;

/**
 *
 * @author ngonga
 */
public class GenericNLG implements Sparql2NLConverter {

    Lexicon lexicon;
    NLGFactory nlgFactory;
    Realiser realiser;

    public GenericNLG() {
        lexicon = Lexicon.getDefaultLexicon();
        nlgFactory = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);
    }

    /** Takes a DBPedia class and returns the correct label for it
     * 
     * @param className Name of a class
     * @return Label
     */
    public NPPhraseSpec getNPPhrase(String className, boolean plural) {
        NPPhraseSpec object = nlgFactory.createNounPhrase(className.toLowerCase());
        object.setPlural(plural);
        return object;
    }

    public String generateNL(Map<String, Set<String>> typeMap, Query query) {

        //first create the beginning of the NLR
        SPhraseSpec p = nlgFactory.createClause();
        p.setSubject("This query");
        p.setVerb("retrieve");
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

        //if only one object go for a simple add, else create a conjunction
        if (objects.size() == 1) {
            p.setObject(objects.get(0));
        } else {
            CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(objects.get(0), objects.get(1));
            if (objects.size() > 2) {
                for (int i = 2; i < objects.size(); i++) {
                    cpe.addCoordinate(objects.get(i));
                }
            }
            p.setObject(cpe);
        }

        //now create complement in form of a such that conjunction
        PhraseElement complement = nlgFactory.createClause("Dave Bus", "be", "born");
        //add complements

        //finally merge the two
        CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(p, complement);
        cpe.setConjunction("such that");
        String output = realiser.realiseSentence(cpe); // Realiser created earlier.
        System.out.println(output);
        //done
        return output;
    }

    public SPhraseSpec getNLForTriple(Triple t) {
        SPhraseSpec p = nlgFactory.createClause();
        //process subject
        if (t.getSubject().isVariable()) {
            p.setSubject(t.getSubject().toString());
        } else {
            p.setSubject(getNPPhrase(t.getSubject().toString(), false));
        }

        //process predicate
        if (t.getPredicate().isVariable()) {
            p.setVerb("relate to");
        } else {
            p.setVerb(getVerbFrom(t.getPredicate()));
        }

        //process object
        if (t.getObject().isVariable()) {
            p.setObject(t.getObject().toString());
        } else {
            p.setObject(getNPPhrase(t.getObject().toString(), false));
        }

        p.setFeature(Feature.TENSE, Tense.PRESENT);
        return p;
    }

    /** Generate a verb for a predicate
     * 
     * @param predicate Node containing the predicate for which the NL is to be generated
     * @return predicate as String
     */
    private String getVerbFrom(Node predicate) {
        return "test";
        //throw new UnsupportedOperationException("Not yet implemented");
    }

    /** Processes a list of elements. These can be elements of the where clause or 
     * of an optional clause
     * @param e List of query elements
     * @return Conjunctive natural representation of the list of elements.
     */    
    public NLGElement getNLFromElements(List<Element> e) {

        if (e.isEmpty()) {
            return null;
        }
        if (e.size() == 1) {
            return getNLFromSingleClause(e.get(0));
        } else {
            CoordinatedPhraseElement cpe;            
            cpe = nlgFactory.createCoordinatedPhrase(getNLFromSingleClause(e.get(0)), getNLFromSingleClause(e.get(1)));
            for (int i = 2; i < e.size(); i++) {                
                cpe.addCoordinate(getNLFromSingleClause(e.get(i)));                
            }            
            cpe.setConjunction("and");
            return cpe;
        }
    }

    public NLGElement getNLFromSingleClause(Element e) {
        // if clause is union clause then we generate or statements
        if (e.getClass() == ElementUnion.class) {
            CoordinatedPhraseElement cpe;
            //cast to union
            ElementUnion union = (ElementUnion) e;
            List<Triple> triples = new ArrayList<Triple>();

            //get all triples. We assume that the depth of union is always 1
            for (Element atom : union.getElements()) {
                Triple t = ((ElementPathBlock) (((ElementGroup) atom).getElements().get(0))).getPattern().get(0).asTriple();
                triples.add(t);
            }
            //if empty
            if (triples.isEmpty()) {
                return null;
            }
            if (triples.size() == 1) {
                return getNLForTriple(triples.get(0));
            } else {
                Triple t0 = triples.get(0);
                Triple t1 = triples.get(1);
                cpe = nlgFactory.createCoordinatedPhrase(getNLForTriple(t0), getNLForTriple(t1));
                for (int i = 2; i < triples.size(); i++) {
                    cpe.addComplement(getNLForTriple(triples.get(i)));
                }
                cpe.setConjunction("or");
                return cpe;
            }
        } //case no union, i.e., just a path block
        else if (e.getClass() == ElementPathBlock.class) {
            Triple t = ((ElementPathBlock) e).getPattern().get(0).asTriple();
            return getNLForTriple(t);
        } // if it's a filter
        else if (e.getClass() == ElementFilter.class) {
            SPhraseSpec p = nlgFactory.createClause();
            ElementFilter filter = (ElementFilter) e;
            Expr expr = filter.getExpr();

            //process REGEX
            if (expr.getClass().equals(E_Regex.class)) {
                E_Regex expression;
                expression = (E_Regex) expr;
                String text = expression.toString();
                text = text.substring(6, text.length() - 1);
                String var = text.substring(0, text.indexOf(","));
                String pattern = text.substring(text.indexOf(",") + 1);
                p.setSubject(var);
                p.setVerb("match");
                p.setObject(pattern);
            }

            //process >

            //process <
            return p;
        }
        return null;
    }

    public static void tenseTest() {
        GenericNLG gnlg = new GenericNLG();
        SPhraseSpec p = gnlg.nlgFactory.createClause();
        p.setSubject("This query");
        p.setVerb("was born in");
        p.setObject("Cameroon");
        //p.setFeature(Feature.TENSE, Feature.PERFECT);
        p.setFeature(Feature.TENSE, Tense.PAST);
        System.out.println(gnlg.realiser.realiseSentence(p));
    }

    public static void test() {
        Map<String, Set<String>> typeMap = new HashMap<String, Set<String>>();
        String s = "x";
        HashSet type = new HashSet();
        type.add("Person");
        //type.add("Village");
        typeMap.put(s, type);

        s = "y";
        type = new HashSet();
        type.add("City");
        //type.add("Village");
        typeMap.put(s, type);
        (new GenericNLG()).generateNL(typeMap, null);
    }

    public String getNLR(com.hp.hpl.jena.query.Query query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public DocumentElement convert2NLE(com.hp.hpl.jena.query.Query query) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
