/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.sparql.expr.E_Regex;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aksw.sparql2nl.queryprocessing.TypeExtractor;
import simplenlg.features.Feature;
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
        System.out.println("Input query = " + query);
        // preprocess the query to get the relevant types
        TypeExtractor tEx = new TypeExtractor();
        Map<String, Set<String>> typeMap = tEx.extractTypes(query);
        System.out.println("Processed query = " + query);
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
            Element e = elt.getElements().get(i);
            if (!(e instanceof ElementOptional)) {
                result.add(e);
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
            Element e = elt.getElements().get(i);
            if (e instanceof ElementOptional) {
                return ((ElementGroup) ((ElementOptional) e).getOptionalElement()).getElements();
            }
        }
        return new ArrayList<Element>();
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
        if (e instanceof ElementUnion) {
            CoordinatedPhraseElement cpe;
            //cast to union
            ElementUnion union = (ElementUnion) e;
            List<Triple> triples = new ArrayList<Triple>();

            //get all triples. We assume that the depth of union is always 1
            for (Element atom : union.getElements()) {
                ElementPathBlock epb = ((ElementPathBlock) (((ElementGroup) atom).getElements().get(0)));
                if (!epb.isEmpty()) {
                    Triple t = epb.getPattern().get(0).asTriple();
                    triples.add(t);
                }
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
        else if (e instanceof ElementPathBlock) {
            ElementPathBlock epb = (ElementPathBlock) e;
            if (!epb.isEmpty()) {
                Triple t = epb.getPattern().get(0).asTriple();
                return getNLForTriple(t);
            }
        } // if it's a filter
        else if (e instanceof ElementFilter) {
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

    public static void main(String args[]) {
        String query = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT DISTINCT ?uri ?x "
                + "WHERE { "
                + "{res:Abraham_Lincoln dbo:deathPlace ?uri} "
                + "UNION {res:Abraham_Lincoln dbo:birthPlace ?uri} . "
                + "?uri rdf:type dbo:Place. "
                + "FILTER regex(?uri, \"France\").  "
                + "OPTIONAL { ?uri dbo:description ?x }. "
                + "}";

        try {
            GenericNLG nlg = new GenericNLG();
            SimpleNLG snlg = new SimpleNLG();
            Query sparqlQuery = QueryFactory.create(query);
            System.out.println("Simple NLG: " + snlg.getNLR(sparqlQuery));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
