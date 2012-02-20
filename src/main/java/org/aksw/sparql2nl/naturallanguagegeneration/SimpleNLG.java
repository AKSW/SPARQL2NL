/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.expr.E_Equals;
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
import java.util.regex.Pattern;
import org.aksw.sparql2nl.queryprocessing.TypeExtractor;
import simplenlg.features.Feature;
import simplenlg.features.Tense;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.framework.PhraseElement;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author ngonga
 */
public class SimpleNLG implements Sparql2NLConverter {

    Lexicon lexicon;
    NLGFactory nlgFactory;
    Realiser realiser;
    public static final String ENTITY = "owl#thing";
    public static final String VALUE = "value";
    public static final String UNKNOWN = "valueOrEntity";
    public static String ENDPOINT = "http://live.dbpedia.org/sparql";
    public static String GRAPH = null;

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
        String output = realiser.realiseSentence(convert2NLE(query));
        output = output.replaceAll(Pattern.quote("\n"), "");
        if (!output.endsWith(".")) {
            output = output + ".";
        }
        return output;
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
        head.setObject(processTypes(typeMap, query.isDistinct(), query.isDistinct()));
        Element e;

        //now generate body
        List<Element> whereElements = getWhereElements(query);
        if (!whereElements.isEmpty()) {
            body = getNLFromElements(whereElements);
            //now add conjunction
            CoordinatedPhraseElement phrase1 = nlgFactory.createCoordinatedPhrase(head, body);
            phrase1.setConjunction("such that");
            // add as first sentence
            sentences.add(nlgFactory.createSentence(phrase1));
            //this concludes the first sentence. 
        } else {
            sentences.add(nlgFactory.createSentence(head));
        }
        // The second sentence deals with the optional clause (if it exists)


        List<Element> optionalElements = getOptionalElements(query);
        if (optionalElements != null && !optionalElements.isEmpty()) {
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

    /** Takes a DBPedia class and returns the correct label for it
     * 
     * @param className Name of a class
     * @return Label
     */
    public NPPhraseSpec getNPPhrase(String className, boolean plural) {
        NPPhraseSpec object;
        if (!className.toLowerCase().contains(ENTITY)) {
            String label = getEnglishLabel(className);
            if (label != null) {
                object = nlgFactory.createNounPhrase(label);
                object.setPlural(plural);
                return object;
            }
        }
        // if something goes wrong, we return entity
        object = nlgFactory.createNounPhrase("entity");
        object.setPlural(plural);
        return object;
    }

    /** Gets the english label of a resource from the specified endpoint and graph
     * 
     * @param resource Resource
     * @return English label, null if none is found
     */
    private String getEnglishLabel(String resource) {
        try {
            String labelQuery = "SELECT ?label WHERE {<" + resource + "> "
                    + "<http://www.w3.org/2000/01/rdf-schema#label> ?label. FILTER (lang(?label) = 'en')}";

            Query sparqlQuery = QueryFactory.create(labelQuery);
            QueryExecution qexec;

            // take care of graph issues. Only takes one graph. Seems like some sparql endpoint do
            // not like the FROM option.

            if (GRAPH != null) {
                qexec = QueryExecutionFactory.sparqlService(ENDPOINT, sparqlQuery, GRAPH);
            } //
            else {
                qexec = QueryExecutionFactory.sparqlService(ENDPOINT, sparqlQuery);
            }
            ResultSet results = qexec.execSelect();

            //get label from knowledge base
            String label = null;
            QuerySolution soln;
            while (results.hasNext()) {
                soln = results.nextSolution();
                // process query here
                {
                    label = soln.get("label").toString();
                }
            }
            if (label != null) {
                if (label.contains("@")) {
                    label = label.substring(0, label.indexOf("@"));
                }
            }
            return label;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private NLGElement processTypes(Map<String, Set<String>> typeMap, boolean count, boolean distinct) {
        List<NPPhraseSpec> objects = new ArrayList<NPPhraseSpec>();
        //process the type information to create the object(s)    
        for (String s : typeMap.keySet()) {
            // contains the objects to the sentence
            NPPhraseSpec object = nlgFactory.createNounPhrase("?"+s);
            Set<String> types = typeMap.get(s);
            for (String type : types) {
                NPPhraseSpec np = getNPPhrase(type, true);
                if (distinct) {
                    np.addModifier("distinct");
                }
                object.addPreModifier(np);
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
            if (expr instanceof E_Regex) {
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

            //process language filter
            if (expr instanceof E_Equals) {
                E_Equals expression;
                expression = (E_Equals) expr;
                String text = expression.toString();
                text = text.substring(1, text.length() - 1);
                String[] split = text.split("=");
                String arg1 = split[0].trim();
                String arg2 = split[1].trim();
                if (arg1.startsWith("lang")) {
                    String var = arg1.substring(5, arg1.length() - 1);
                    p.setSubject(var);
                    p.setVerb("be in");
                    if (arg2.contains("en")) {
                        p.setObject("English");
                    }
                } else {
                    p.setSubject(arg1);
                    p.setVerb("equal");
                    p.setObject(arg2);
                }
            }
            //process <
            return p;
        }
        return null;
    }

    public SPhraseSpec getNLForTriple2(Triple t) {
        SPhraseSpec p = nlgFactory.createClause();
        //process subject
        if (t.getSubject().isVariable()) {
            p.setSubject(t.getSubject().toString());
        } else {
            p.setSubject(getNPPhrase(t.getSubject().toString(), false));
        }

        //process predicate
        if (t.getPredicate().isVariable()) {
            p.setVerb("relate via " + t.getPredicate() + " to");
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

    public SPhraseSpec getNLForTriple(Triple t) {
        SPhraseSpec p = nlgFactory.createClause();
        //process predicate then return subject is related to
        if (t.getPredicate().isVariable()) {
            if (t.getSubject().isVariable()) {
                p.setSubject(t.getSubject().toString());
            } else {
                p.setSubject(getNPPhrase(t.getSubject().toString(), false));                
            }
            p.setVerb("be relate via \"" + getEnglishLabel(t.getPredicate().toString()) + "\" to");
            if (t.getObject().isVariable()) {
                p.setObject(t.getObject().toString());
            } else {
                p.setObject(getNPPhrase(t.getObject().toString(), false));
            }
        } else {
            NLGElement subj;
            if (t.getSubject().isVariable()) {
                subj = nlgFactory.createWord(t.getSubject().toString(), LexicalCategory.NOUN);
            } else {
                subj = nlgFactory.createWord(getEnglishLabel(t.getSubject().toString()), LexicalCategory.NOUN);
            }
    //        subj.setFeature(Feature.POSSESSIVE, true);            
    //        PhraseElement np = nlgFactory.createNounPhrase(subj, getEnglishLabel(t.getPredicate().toString()));
            p.setSubject(realiser.realise(subj)+"\'s "+getEnglishLabel(t.getPredicate().toString()));
            p.setVerb("be");
            if (t.getObject().isVariable()) {
                p.setObject(t.getObject().toString());
            } else {
                p.setObject(getNPPhrase(t.getObject().toString(), false));
            }
        }
        p.setFeature(Feature.TENSE, Tense.PRESENT);
        return p;
    }

    private String getVerbFrom(Node predicate) {
        return "test";
        //throw new UnsupportedOperationException("Not yet implemented");
    }

    public static void main(String args[]) {
        String query2 = "PREFIX res: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT DISTINCT ?height "
                + "WHERE { res:Claudia_Schiffer dbo:height ?height . }";
        
        String query = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT count(DISTINCT ?uri) "
                + "WHERE { "
                + "{res:Abraham_Lincoln dbo:deathPlace ?uri} "
                + "UNION {res:Abraham_Lincoln dbo:birthPlace ?uri} . "
                + "?uri rdf:type dbo:Place. "
                + "FILTER regex(?uri, \"France\").  "
                + "FILTER (lang(?uri) = 'en')"
                + "OPTIONAL { ?uri dbo:Name ?x }. "
                + "}";
        String query3 = "PREFIX dbo: <http://dbpedia.org/ontology/> PREFIX yago: <http://dbpedia.org/class/yago/> SELECT COUNT(DISTINCT ?uri) WHERE { ?uri rdf:type yago:EuropeanCountries . ?uri dbo:governmentType ?govern . FILTER regex(?govern,'monarchy') . }";
        try {
            SimpleNLG snlg = new SimpleNLG();
            Query sparqlQuery = QueryFactory.create(query3, Syntax.syntaxSPARQL_11);
            System.out.println(query);
            System.out.println("Simple NLG: Query is distinct = " + sparqlQuery.isDistinct());
            System.out.println("Simple NLG: " + snlg.getNLR(sparqlQuery));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
