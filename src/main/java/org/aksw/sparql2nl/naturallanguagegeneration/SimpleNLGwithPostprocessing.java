package org.aksw.sparql2nl.naturallanguagegeneration;

import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.aksw.sparql2nl.naturallanguagegeneration.PropertyProcessor.Type;
import org.aksw.sparql2nl.nlp.relation.BoaPatternSelector;
import org.aksw.sparql2nl.nlp.stemming.PlingStemmer;
import org.aksw.sparql2nl.queryprocessing.DisjunctiveNormalFormConverter;
import org.aksw.sparql2nl.queryprocessing.GenericType;
import org.aksw.sparql2nl.queryprocessing.TypeExtractor;
import org.dllearner.kb.sparql.SparqlEndpoint;

import simplenlg.features.Feature;
import simplenlg.features.Tense;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.graph.impl.LiteralLabel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.SortCondition;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
import com.hp.hpl.jena.sparql.expr.ExprVar;
import com.hp.hpl.jena.sparql.expr.aggregate.AggCountVar;
import com.hp.hpl.jena.sparql.expr.aggregate.Aggregator;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementOptional;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.syntax.PatternVars;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.hp.hpl.jena.vocabulary.XSD;

/**
 *
 * @author ngonga
 */
public class SimpleNLGwithPostprocessing implements Sparql2NLConverter {

    Lexicon lexicon;
    NLGFactory nlgFactory;
    Realiser realiser;
    private URIConverter uriConverter;
    private LiteralConverter literalConverter;
    private FilterExpressionConverter expressionConverter;
    Postprocessor post;
    public static final String ENTITY = "owl#thing";
    public static final String VALUE = "value";
    public static final String UNKNOWN = "valueOrEntity";
    public boolean VERBOSE = false;
    public boolean POSTPROCESSING;
    private boolean SWITCH;
    private boolean UNIONSWITCH;
    private Set<Set<SPhraseSpec>> UNION;
    private Set<SPhraseSpec> union;
    private NLGElement select;
    
    private boolean useBOA = false;
    private SparqlEndpoint endpoint;
    
    private PropertyProcessor pp;
    

    public SimpleNLGwithPostprocessing(SparqlEndpoint endpoint) {
        this.endpoint = endpoint;

        lexicon = Lexicon.getDefaultLexicon();
        nlgFactory = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);

        post = new Postprocessor();
        post.id = 0;

        uriConverter = new URIConverter(endpoint);
        literalConverter = new LiteralConverter(uriConverter);
        expressionConverter = new FilterExpressionConverter(uriConverter, literalConverter);
        
        pp = new PropertyProcessor("resources/wordnet/dict");

    }
    
    public SimpleNLGwithPostprocessing(SparqlEndpoint endpoint, String wordnetDir) {
        this.endpoint = endpoint;

        lexicon = Lexicon.getDefaultLexicon();
        nlgFactory = new NLGFactory(lexicon);
        realiser = new Realiser(lexicon);

        post = new Postprocessor();
        post.id = 0;

        uriConverter = new URIConverter(endpoint);
        literalConverter = new LiteralConverter(uriConverter);
        expressionConverter = new FilterExpressionConverter(uriConverter, literalConverter);
        
        pp = new PropertyProcessor(wordnetDir);

    }
    
    public void setUseBOA(boolean useBOA) {
		this.useBOA = useBOA;
	}

    public String realiseDocument(DocumentElement d) {
        String output = "";
        for (NLGElement s : d.getComponents()) {
            String sentence = realiser.realiseSentence(s);
            if (!sentence.endsWith(".")) {
                sentence = sentence + ".";
            }
            output = output + " " + sentence;
        }
        if (!output.isEmpty()) output = output.substring(1);
        return output;
    }

    /**
     * Converts the representation of the query as Natural Language Element into
     * free text.
     *
     * @param query Input query
     * @return Text representation
     */
    @Override
    public String getNLR(Query inputQuery) {
    	//we copy the query object here, because during the NLR generation it will be modified 
    	Query query = QueryFactory.create(inputQuery);
    	query = new DisjunctiveNormalFormConverter().getDisjunctiveNormalForm(query);
        
        String output = "";

        // 1. run convert2NLE and in parallel assemble postprocessor
        POSTPROCESSING = false;
        SWITCH = false;
        UNIONSWITCH = false;
        output = realiseDocument(convert2NLE(query));
        System.out.println("\nSimpleNLG:\n" + output);
        if (VERBOSE) post.TRACE = true;

        // 2. run postprocessor
        post.postprocess();

        // 3. run convert2NLE again, but this time use body generations from postprocessor
        POSTPROCESSING = true;
        output = realiseDocument(convert2NLE(query));
        output = output.replace(",,", ",").replace("..", ".").replace(".,",","); // wherever this duplicate punctuation comes from...
//        output = post.removeDots(output)+".";
        System.out.println("\nAfter postprocessing:\n" + output);

        post.flush();

        output = output.replaceAll(Pattern.quote("\n"), "");
        return output;
    }
    

    /**
     * Generates a natural language representation for a query
     *
     * @param query Input query
     * @return Natural Language Representation
     */
    @Override
    public DocumentElement convert2NLE(Query query) {
        if (query.isSelectType() || query.isAskType()) {
            return convertSelectAndAsk(query);
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

    /**
     * Generates a natural language representation for SELECT queries
     *
     * @param query Input query
     * @return Natural Language Representation
     */
    public DocumentElement convertSelectAndAsk(Query query) {
        // List of sentences for the output
        List<DocumentElement> sentences = new ArrayList<DocumentElement>();
//        System.out.println("Input query = " + query);
        // preprocess the query to get the relevant types
        TypeExtractor tEx = new TypeExtractor(endpoint);
        Map<String, Set<String>> typeMap = tEx.extractTypes(query);
//        System.out.println("Processed query = " + query);
        // contains the beginning of the query, e.g., "this query returns"
        SPhraseSpec head = nlgFactory.createClause();
        String conjunction = "such that";
        NLGElement body;
        NLGElement postConditions;

        List<Element> whereElements = getWhereElements(query);
        List<Element> optionalElements = getOptionalElements(query);
        // first sort out variables
        Set<String> projectionVars = typeMap.keySet();
        Set<String> whereVars = getVars(whereElements, projectionVars);
        // case we only have stuff such as rdf:type queries
        if (whereVars.isEmpty()) {
            //whereVars = projectionVars
            whereVars = tEx.explicitTypedVars;
        }
        Set<String> optionalVars = getVars(optionalElements, projectionVars);
        //important. Remove variables that have already been declared in first
        //sentence from second sentence
        for (String var : whereVars) {
            if (optionalVars.contains(var)) {
                optionalVars.remove(var);
            }
        }

        // collect primary and secondary variables for postprocessor
        if (!POSTPROCESSING) {
            post.primaries = typeMap.keySet();
            List<String> nonoptionalVars = new ArrayList<String>();
            for (Element e : whereElements) {
                for (Var var : PatternVars.vars(e)) {
                    String v = var.toString().replace("?", "");
                    if (!optionalVars.contains(v) && !typeMap.containsKey(v)) {
                        post.addSecondary(v);
                    }
                }
            }
        }

        //process ASK queries
        if (query.isAskType()) {
            post.ask = true;
            //process factual ask queries (no variables at all)
            head.setSubject("This query");
            head.setVerb("ask whether");
            
            if (POSTPROCESSING) {
                if (!post.selects.isEmpty()) {
                    if (post.selects.size() == 1 && post.output == null) {
                        head.setVerb("ask whether there is such a thing as");
                        head.setObject(post.selects.get(0));
                        sentences.add(nlgFactory.createSentence(head));
                    }
                    else if (post.selects.size() > 1 && post.output == null) {
                        head.setVerb("ask whether there are such things as");
                        CoordinatedPhraseElement obj = nlgFactory.createCoordinatedPhrase();
                        obj.setConjunction("and");
                        for (NPPhraseSpec np : post.selects) obj.addCoordinate(np);
                        head.setObject(obj);
                        sentences.add(nlgFactory.createSentence(head));
                    }
                    else if (post.output != null) {
                        head.setVerb("ask whether there are");
                        if (post.selects.size() == 1)
                            head.setObject(post.selects.get(0));
                        else {
                            CoordinatedPhraseElement obj = nlgFactory.createCoordinatedPhrase();
                            obj.setConjunction("and");
                            for (NPPhraseSpec np : post.selects) obj.addCoordinate(np);
                            head.setObject(obj);
                        }
                        CoordinatedPhraseElement p = nlgFactory.createCoordinatedPhrase(head,post.output);
                        if (post.relativeClause) p.setConjunction("");
                        else p.setConjunction("such that");
                        sentences.add(nlgFactory.createSentence(p));
                    }
                }
                else {
                    if (post.output != null && !realiser.realise(post.output).toString().trim().isEmpty()) {
                        head.setObject(post.output);
                        sentences.add(nlgFactory.createSentence(head));
                    }                    
                }
            }
            else { // head.setObject(getNLFromElements(whereElements)) is correct but leads to a bug
                head.setObject(realiser.realise(getNLFromElements(whereElements)));
                head.getObject().setFeature(Feature.SUPRESSED_COMPLEMENTISER,true);
                sentences.add(nlgFactory.createSentence(realiser.realise(head)));
            }
            if (typeMap.isEmpty()) return nlgFactory.createParagraph(sentences);
            
        }
        else {
        //process SELECT queries
        
            head.setSubject("This query");
            head.setVerb("retrieve");
        
            if (POSTPROCESSING) select = post.returnSelect();
            else // this is done in the first run and select is then set also for the second (postprocessing) run
                select = processTypes(typeMap, whereVars, tEx.isCount(), query.isDistinct());  // if tEx.isCount(), this gives "number of" + select
        
            head.setObject(select);
            head.getObject().setFeature(Feature.SUPRESSED_COMPLEMENTISER,true);
            //now generate body
            if (!whereElements.isEmpty() || post.output != null) {
                if (POSTPROCESSING) {
                    body = post.output;
                } else {
                    body = getNLFromElements(whereElements);
                }
                //now add conjunction
                CoordinatedPhraseElement phrase1 = nlgFactory.createCoordinatedPhrase(head, body);
                if (POSTPROCESSING && post.relativeClause) phrase1.setConjunction("");
                else phrase1.setConjunction("such that");
                // add as first sentence
                sentences.add(nlgFactory.createSentence(phrase1));
                //this concludes the first sentence.
            } else {
                sentences.add(nlgFactory.createSentence(head));
            }
        }
        
        /*
         head.setObject(select);
            //now generate body
            if (!whereElements.isEmpty() || post.output != null) {
                if (POSTPROCESSING) {
                    body = post.output;
                } else {
                    body = getNLFromElements(whereElements);
                }
                //now add conjunction
                CoordinatedPhraseElement phrase1 = nlgFactory.createCoordinatedPhrase(head, body);
                phrase1.setConjunction("such that");
                // add as first sentence
                sentences.add(nlgFactory.createSentence(phrase1));
                //this concludes the first sentence.
            } else {
                sentences.add(nlgFactory.createSentence(head));
            }
         */

        // The second sentence deals with the optional clause (if it exists)
        if (!POSTPROCESSING && optionalElements != null && !optionalElements.isEmpty()) {
            SWITCH = true;
            //the optional clause exists
            //if no supplementary projection variables are used in the clause
            if (optionalVars.isEmpty()) {
                SPhraseSpec optionalHead = nlgFactory.createClause();
                optionalHead.setSubject("it");
                optionalHead.setVerb("retrieve");
                optionalHead.setObject("data");
                optionalHead.setFeature(Feature.CUE_PHRASE, "Additionally, ");
                NLGElement optionalBody;
                optionalBody = getNLFromElements(optionalElements);
                CoordinatedPhraseElement optionalPhrase = nlgFactory.createCoordinatedPhrase(optionalHead, optionalBody);
                optionalPhrase.setConjunction("such that");
                optionalPhrase.addComplement("if such exist");
                sentences.add(nlgFactory.createSentence(optionalPhrase));

            } //if supplementary projection variables are used in the clause
            else {
                SPhraseSpec optionalHead = nlgFactory.createClause();
                optionalHead.setSubject("it");
                optionalHead.setVerb("retrieve");
                optionalHead.setObject(processTypes(typeMap, optionalVars, tEx.isCount(), query.isDistinct()));
                optionalHead.setFeature(Feature.CUE_PHRASE, "Additionally, ");
                if (!optionalElements.isEmpty()) {
                    NLGElement optionalBody;
                    optionalBody = getNLFromElements(optionalElements);
                    //now add conjunction
                    CoordinatedPhraseElement optionalPhrase = nlgFactory.createCoordinatedPhrase(optionalHead, optionalBody);
                    optionalPhrase.setConjunction("such that");
                    // add as second sentence
                    optionalPhrase.addComplement("if such exist");
                    sentences.add(nlgFactory.createSentence(optionalPhrase));
                    //this concludes the second sentence.
                } else {
                    optionalHead.addComplement("if such exist");
                    sentences.add(nlgFactory.createSentence(optionalHead));
                }
            }
            SWITCH = false;
        }

        //The last sentence deals with the result modifiers
        if (POSTPROCESSING && post.additionaloutput != null) {
            sentences.add(nlgFactory.createSentence(post.additionaloutput));
        }
        else if (!POSTPROCESSING) {
            if (query.hasHaving()) {
                SPhraseSpec modifierHead = nlgFactory.createClause();
                modifierHead.setSubject("it");
                modifierHead.setVerb("return exclusively");
                modifierHead.setObject("results");
                modifierHead.getObject().setPlural(true);
                modifierHead.setFeature(Feature.CUE_PHRASE, "Moreover, ");
                List<Expr> expressions = query.getHavingExprs();
    //            CoordinatedPhraseElement phrase = nlgFactory.createCoordinatedPhrase(modifierHead, getNLFromExpressions(expressions));
                NLGElement phrase = getNLFromExpressions(expressions);
                phrase.setFeature("premodifier","such that");
                modifierHead.addComplement(phrase);
                if (!POSTPROCESSING) {
                    post.orderbylimit.add(new Sentence(modifierHead,false,post.id)); post.id++;
                    sentences.add(nlgFactory.createSentence(modifierHead));
                }   
            }
            if (query.hasOrderBy()) {
                SPhraseSpec order = nlgFactory.createClause();
                order.setSubject("The results");
                order.getSubject().setPlural(true);
                order.setVerb("be in");
                List<SortCondition> sc = query.getOrderBy();
                if (sc.size() == 1) {
                    int direction = sc.get(0).getDirection();
                    if (direction == Query.ORDER_DESCENDING) {
                        order.setObject("descending order");
                    } else if (direction == Query.ORDER_ASCENDING || direction == Query.ORDER_DEFAULT){
                        order.setObject("ascending order");
                    }
                    Expr expr = sc.get(0).getExpression();
                    if (expr instanceof ExprVar) {
                        ExprVar ev = (ExprVar) expr;
                        order.addComplement("with respect to "+ev.toString()+"");
                    }
                }
                if (!POSTPROCESSING) {
                    post.orderbylimit.add(new Sentence(order,false,post.id)); post.id++;
                    sentences.add(nlgFactory.createSentence(order));
                } 
            }
            if (query.hasLimit()) {
                SPhraseSpec limitOffset = nlgFactory.createClause();
                long limit = query.getLimit();
                if (query.hasOffset()) {
                    long offset = query.getOffset();
                    limitOffset.setSubject("The query");
                    limitOffset.setVerb("return");
                    if(limit == 1){
                            String ending;
                            switch ((int)offset+1){
                                    case 1: ending = "st"; break;
                                    case 2: ending = "nd"; break;
                                    case 3: ending = "rd"; break;
                                    default: ending = "th"; 
                            }
                            limitOffset.setObject("the " + (limit + offset) + ending + " result");

                    } else {
                            limitOffset.setObject("results between number " + (offset+1) + " and " + (offset + limit));
                    }


                } else {
                    limitOffset.setSubject("The query");
                    limitOffset.setVerb("return");
                    if (limit > 1) {
                        if (query.hasOrderBy()) {
                            limitOffset.setObject("the first " + limit + " results");
                        } else {
                            limitOffset.setObject( limit + " results");
                        }
                    } else {
                        if (query.hasOrderBy()) {
                            limitOffset.setObject("the first result");
                        } else {
                            limitOffset.setObject("one result");
                        }
                    }
                }
                if (!POSTPROCESSING) {
                    post.orderbylimit.add(new Sentence(limitOffset,false,post.id)); post.id++;
                    sentences.add(nlgFactory.createSentence(limitOffset));
                } 
            }
        }

        DocumentElement result = nlgFactory.createParagraph(sentences);
        return result;
    }

    /**
     * Fetches all elements of the query body, i.e., of the WHERE clause of a
     * query
     *
     * @param query Input query
     * @return List of elements from the WHERE clause
     */
    private static List<Element> getWhereElements(Query query) {
        List<Element> result = new ArrayList<Element>();
        Element f = query.getQueryPattern();
        ElementGroup elt = (ElementGroup) query.getQueryPattern();
        for (int i = 0; i < elt.getElements().size(); i++) {
            Element e = elt.getElements().get(i);
            if (!(e instanceof ElementOptional)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Fetches all elements of the optional, i.e., of the OPTIONAL clause. query
     *
     * @param query Input query
     * @return List of elements from the OPTIONAL clause if there is one, else
     * null
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

    /**
     * Takes a DBPedia class and returns the correct label for it
     *
     * @param className Name of a class
     * @return Label
     */
    public NPPhraseSpec getNPPhrase(String className, boolean plural) {
        NPPhraseSpec object = null;
        if (className.equals(OWL.Thing.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
        } else if (className.equals(RDFS.Literal.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
        } else if (className.equals(RDF.Property.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
        } else if (className.equals(RDF.type.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
        } else {
            String label = uriConverter.convert(className);
            if (label != null) {
            	label = PlingStemmer.stem(label);
                object = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(label, LexicalCategory.NOUN));
            } else {
                object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
            }
          
        }
        object.setPlural(plural);
       
        return object;
    }
    
    public NPPhraseSpec getNPPhrase(String uri, boolean plural, boolean isClass) {
        NPPhraseSpec object = null;
        if (uri.equals(OWL.Thing.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
        } else if (uri.equals(RDFS.Literal.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.VALUE.getNlr());
        } else if (uri.equals(RDF.Property.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.RELATION.getNlr());
        } else if (uri.equals(RDF.type.getURI())) {
            object = nlgFactory.createNounPhrase(GenericType.TYPE.getNlr());
        } else {
            String label = uriConverter.convert(uri);
            if (label != null) {
            	if(isClass){
            		label = PlingStemmer.stem(label);
            	}
                object = nlgFactory.createNounPhrase(nlgFactory.createInflectedWord(label, LexicalCategory.NOUN));
            } else {
                object = nlgFactory.createNounPhrase(GenericType.ENTITY.getNlr());
            }
          
        }
        object.setPlural(plural);
       
        return object;
    }

    private NLGElement processTypes(Map<String, Set<String>> typeMap, Set<String> vars, boolean count, boolean distinct) {
        List<NPPhraseSpec> objects = new ArrayList<NPPhraseSpec>();
        //process the type information to create the object(s)
        for (String s : typeMap.keySet()) {
            if (vars.contains(s)) {
                // contains the objects to the sentence
                NPPhraseSpec object;
                object = nlgFactory.createNounPhrase("?" + s);
                Set<String> types = typeMap.get(s);
                if (types.size() == 1) {
                    NPPhraseSpec np = getNPPhrase(types.iterator().next(), true);
                    if (count) {
                        np.addPreModifier("the number of");
                    }
                    if (distinct) {
                        np.addModifier("distinct");
                    }
                    np.setPlural(true);
                    object.addPreModifier(np);                   
                } else {
                    Iterator<String> typeIterator = types.iterator();
                    String type0 = typeIterator.next();
                    String type1 = typeIterator.next();
                    NPPhraseSpec np0 = getNPPhrase(type0, true);
//                        if (distinct) {
//                            np0.addModifier("distinct");
//                        }
                    NPPhraseSpec np1 = getNPPhrase(type1, true);
//                        if (distinct) {
//                            np1.addModifier("distinct");
//                        }
                    CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(np0, np1);
                    while (typeIterator.hasNext()) {
                        NPPhraseSpec np = getNPPhrase(typeIterator.next(), true);
//                        if (distinct) {
//                            np.addModifier("distinct");
//                        }
                        cpe.addCoordinate(np);
                    }
                    cpe.setConjunction("as well as");
                    if (distinct) {
                        cpe.addPreModifier("distinct");
                    }
                    object.addPreModifier(cpe);
                }
                object.setFeature(Feature.CONJUNCTION, "or");
                objects.add(object);
            }
        }

        post.selects.addAll(objects);

        if (objects.size() == 1) {
            //if(count) objects.get(0).addPreModifier("the number of");
            return objects.get(0);
        } else {
            CoordinatedPhraseElement cpe = nlgFactory.createCoordinatedPhrase(objects.get(0), objects.get(1));
            if (objects.size() > 2) {
                for (int i = 2; i < objects.size(); i++) {
                    cpe.addCoordinate(objects.get(i));
                }
            }
            //if(count) cpe.addPreModifier("the number of");
            return cpe;
        }
    }

    public DocumentElement convertDescribe(Query query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Processes a list of elements. These can be elements of the where clause
     * or of an optional clause
     *
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

    public NLGElement getNLForTripleList(List<Triple> triples, String conjunction) {
        
        if (triples.isEmpty()) return null;
        
        if (triples.size() == 1) {
            SPhraseSpec p = getNLForTriple(triples.get(0));
            if (UNIONSWITCH) union.add(p);
            else {
                if (SWITCH) { addTo(post.sentences,new Sentence(p,true,post.id)); post.id++; }
                else { addTo(post.sentences,new Sentence(p,false,post.id)); post.id++; }
            }
            return p;
        } else { // the following code is a bit redundant...
            // feed the postprocessor
            SPhraseSpec p;
            for (int i = 0; i < triples.size(); i++) {
                p = getNLForTriple(triples.get(i));
                if (UNIONSWITCH) union.add(p); 
                else {
                    if (SWITCH) { addTo(post.sentences,new Sentence(p,true,post.id)); post.id++; }
                    else { addTo(post.sentences,new Sentence(p,false,post.id)); post.id++; }
                }
            }

            // do simplenlg
            CoordinatedPhraseElement cpe;
            Triple t0 = triples.get(0);
            Triple t1 = triples.get(1);
            cpe = nlgFactory.createCoordinatedPhrase(getNLForTriple(t0), getNLForTriple(t1));
            for (int i = 2; i < triples.size(); i++) {
                cpe.addCoordinate(getNLForTriple(triples.get(i)));
            }
            cpe.setConjunction(conjunction);
            return cpe;
        }
    }

    public NLGElement getNLFromSingleClause(Element e) {
        if (e instanceof ElementPathBlock) {
            ElementPathBlock epb = (ElementPathBlock) e;
            List<Triple> triples = new ArrayList<Triple>();

            // get all triples
            for (TriplePath tp : epb.getPattern().getList()) {
                Triple t = tp.asTriple();
                triples.add(t);
            }
            return getNLForTripleList(triples, "and");
        } // if clause is union clause then we generate or statements
        else if (e instanceof ElementUnion) { 
            CoordinatedPhraseElement cpe;
            //cast to union
            ElementUnion union = (ElementUnion) e;

            // for POSTPROCESSOR
            UNIONSWITCH = true; 
            UNION = new HashSet<Set<SPhraseSpec>>();

            // get all triples
            List<NLGElement> list = new ArrayList<NLGElement>();
            for (Element atom : union.getElements()) {
                list.add(getNLFromSingleClause(atom)); 
            }
            
            // for POSTPROCESSOR
            Set<Set<Sentence>> UNIONclone = new HashSet<Set<Sentence>>();
            for (Set<SPhraseSpec> UN : UNION) {
                Set<Sentence> UNclone = new HashSet<Sentence>();
                for (SPhraseSpec s : UN) {
                    UNclone.add(new Sentence(s,SWITCH,post.id));
                    post.id++;
                }
                UNIONclone.add(UNclone);
            }
            if (SWITCH) post.unions.add(new Union(UNIONclone,true));
            else post.unions.add(new Union(UNIONclone,false));
                        
            UNIONSWITCH = false;
            UNION = new HashSet<Set<SPhraseSpec>>();
            
            //should not happen
            if(list.size()==0) return null; 
            if(list.size()==1) return list.get(0);
            else
            {
                cpe = nlgFactory.createCoordinatedPhrase(list.get(0), list.get(1));
                for(int i=2; i<list.size(); i++)
                    cpe.addCoordinate(list.get(i));
                cpe.setConjunction("or");
            }
            
            return cpe;
            //return getNLForTripleList(triples, "or");
        } // if it's a filter
        else if (e instanceof ElementFilter) {
            ElementFilter filter = (ElementFilter) e;
            Expr expr = filter.getExpr();
            NLGElement el = getNLFromSingleExpression(expr);
            if (!POSTPROCESSING) { 
                  if (el.getClass().toString().endsWith("SPhraseSpec")) {
                      post.filters.add(new Filter(new Sentence(((SPhraseSpec) el),false,post.id)));
                      post.id++;
                  }
                  else if (el.getClass().toString().endsWith("CoordinatedPhraseElement")) {
                      String coord = ((CoordinatedPhraseElement) el).getConjunction();
                      Set<Sentence> csents = new HashSet<Sentence>();
                      for (NLGElement compl : ((CoordinatedPhraseElement) el).getChildren()) {
                        csents.add(new Sentence(((SPhraseSpec) compl),false,post.id));
                        post.id++;
                      }
                      post.filters.add(new Filter(csents,coord));
                  }
            }
            return el;
        }
	if (e instanceof ElementGroup) {
            if (UNIONSWITCH) union = new HashSet<SPhraseSpec>();
            
            if (((ElementGroup) e).getElements().size() == 1) {
		NLGElement el = getNLFromSingleClause(((ElementGroup) e).getElements().get(0));
                if (UNIONSWITCH) UNION.add(union);
                return el;
            } else {
	          CoordinatedPhraseElement cpe;
		  List<NLGElement> list = new ArrayList<NLGElement>();
		  for (Element elt : ((ElementGroup) e).getElements()) {
		       list.add(getNLFromSingleClause(elt));
		  }
                  if (UNIONSWITCH) UNION.add(union);
                  
		  cpe = nlgFactory.createCoordinatedPhrase(list.get(0), list.get(1));
		  for(int i=2; i<list.size(); i++) 
                      cpe.addCoordinate(list.get(i));
                  cpe.setConjunction("and");
                  return cpe;
	    }  
	}
        return null;
    }


    public SPhraseSpec getNLForTriple(Triple t) {
        SPhraseSpec p = nlgFactory.createClause();
        //process predicate
        //start with variables
        if (t.getPredicate().isVariable()) {
            //if subject is variable then use variable label, else generate textual representation
            if (t.getSubject().isVariable()) {
                p.setSubject(t.getSubject().toString());
            } else {
                p.setSubject(getNPPhrase(t.getSubject().toString(), false));
            }

            // predicate is variable, thus simply use variable label
            p.setVerb("be related via " + t.getPredicate().toString() + " to");
            
            
            //then process the object
            Object object;
            if (t.getObject().isVariable()) {
                object = t.getObject().toString();
            } else if (t.getObject().isLiteral()) {
            	LiteralLabel lit = t.getObject().getLiteral();
            	NPPhraseSpec np = nlgFactory.createNounPhrase(
            			nlgFactory.createInflectedWord(literalConverter.convert(lit), LexicalCategory.NOUN));
                np.setPlural(literalConverter.isPlural(lit));
                object = np;
            } else {
                object = getNPPhrase(t.getObject().toString(), false);
            }
            p.setObject(object);
        } //more interesting case. Predicate is not a variable
        //then check for noun and verb. If the predicate is a noun or a verb, then
        //use possessive or verbal form, else simply get the boa pattern
        else {
            // first get the string representation for the subject
            NLGElement subj;
            if (t.getSubject().isVariable()) {
                subj = nlgFactory.createWord(t.getSubject().toString(), LexicalCategory.NOUN);
            } else {
                subj = nlgFactory.createWord(uriConverter.convert(t.getSubject().toString()), LexicalCategory.NOUN);
            }

            //then process the object
            Object object;
            if (t.getObject().isVariable()) {
                object = t.getObject().toString();
            } else if (t.getObject().isLiteral()) {
            	LiteralLabel lit = t.getObject().getLiteral();
            	NPPhraseSpec np = nlgFactory.createNounPhrase(
            			nlgFactory.createInflectedWord(literalConverter.convert(lit), LexicalCategory.NOUN));
                np.setPlural(literalConverter.isPlural(lit));
                object = np;
            } else {
                object = getNPPhrase(t.getObject().toString(), false, t.getPredicate().matches(RDF.type.asNode()));
            }
            
            //handle the predicate
            String predicateAsString = uriConverter.convert(t.getPredicate().toString());

            //convert camelcase to single words
            String regex = "([a-z])([A-Z])";
            String replacement = "$1 $2";
            predicateAsString = predicateAsString.replaceAll(regex, replacement).toLowerCase();
            if(predicateAsString.contains("(")) predicateAsString = predicateAsString.substring(0, predicateAsString.indexOf("("));
            //System.out.println(predicateAsString);
            Type type;
            if(t.getPredicate().matches(RDFS.label.asNode())){
            	type = Type.NOUN;
            } else {
            	type = pp.getType(predicateAsString);
            }
            
            
         // if the predicate is rdf:type
            if (t.getPredicate().matches(RDF.type.asNode())) {
                p.setSubject(subj);
                p.setVerb("be a");
                p.setObject(object);
            } else

            // now if the predicate is a noun
            if (type == Type.NOUN) {
                String realisedsubj = realiser.realise(subj).getRealisation();
                if (realisedsubj.endsWith("s")) {
                    realisedsubj += "\' ";
                } else {
                    realisedsubj += "\'s ";
                }
                p.setSubject(realisedsubj + PlingStemmer.stem(predicateAsString));
                p.setVerb("be");
                p.setObject(object);
            } // if the predicate is a verb
            else if (type == Type.VERB) {
                p.setSubject(subj);
                p.setVerb(pp.getInfinitiveForm(predicateAsString));
                p.setObject(object);
            } //in other cases, use the BOA pattern
            else {

                List<org.aksw.sparql2nl.nlp.relation.Pattern> l = BoaPatternSelector.getNaturalLanguageRepresentation(t.getPredicate().toString(), 1);
                if (l.size() > 0) {
                    String boaPattern = l.get(0).naturalLanguageRepresentation;
                    //range before domain
                    if (boaPattern.startsWith("?R?")) {
                        p.setSubject(subj);
                        p.setObject(object);
                    } else {
                        p.setObject(subj);
                        p.setSubject(object);
                    }
                    p.setVerb(BoaPatternSelector.getNaturalLanguageRepresentation(t.getPredicate().toString(), 1).get(0).naturalLanguageRepresentationWithoutVariables);
                } //last resort, i.e., no boa pattern found
                else {
                    p.setSubject(subj);
                    p.setVerb("be related via \"" + predicateAsString + "\" to");
                    p.setObject(object);
                }
            }
        }
        p.setFeature(Feature.TENSE, Tense.PRESENT);

        return p;
    } 
    
    private NLGElement processSubject(Node node){
    	NLGElement subj;
        if (node.isVariable()) {
            subj = nlgFactory.createWord(node.toString(), LexicalCategory.NOUN);
        } else if(node.isURI()){
            subj = nlgFactory.createWord(uriConverter.convert(node.getURI()), LexicalCategory.NOUN);
        } else {
        	throw new UnsupportedOperationException("Blank nodes are not supported yet.");
        }
        return subj;
    }
    
    private void processPredicate(Node node){
    	
    }

    private void processObject(Node node){
	
    }
    
    private String normalizeVerb(String verb){
    	if(verb.startsWith("to ")){
    		verb = verb.replace("to ", "");
    	}
    	return verb;
    }

    private Set<String> getVars(List<Element> elements, Set<String> projectionVars) {
        Set<String> result = new HashSet<String>();
        for (Element e : elements) {
            for (String var : projectionVars) {
                if (e.toString().contains("?" + var)) {
                    result.add(var);
                }
            }
        }
        return result;
    }

    private NLGElement getNLFromSingleExpression(Expr expr) {
        return expressionConverter.convert(expr);
    }

    private NLGElement getNLGFromAggregation(ExprAggregator aggregationExpr) {
        SPhraseSpec p = nlgFactory.createClause();
        Aggregator aggregator = aggregationExpr.getAggregator();
        Expr expr = aggregator.getExpr();
        if (aggregator instanceof AggCountVar) {
            p.setSubject("the number of " + expr);
        }
        return p.getSubject();
    }

    private NLGElement getNLFromExpressions(List<Expr> expressions) {
        List<NLGElement> nlgs = new ArrayList<NLGElement>();
        NLGElement elt;
        for (Expr e : expressions) {
            elt = getNLFromSingleExpression(e);
            if (elt != null) {
                nlgs.add(elt);
            }
        }
        //now process
        if (nlgs.isEmpty()) {
            return null;
        }
        if (nlgs.size() == 1) {
            return nlgs.get(0);
        } else {
            CoordinatedPhraseElement cpe;
            cpe = nlgFactory.createCoordinatedPhrase(nlgs.get(0), nlgs.get(1));
            for (int i = 2; i < nlgs.size(); i++) {
                cpe.addCoordinate(nlgs.get(i));
            }
            cpe.setConjunction("and");
            return cpe;
        }
    }
    
    private void addTo(Set<Sentence> sentences, Sentence sent) {
        boolean duplicate = false;
        for (Sentence s : sentences) {
            if (realiser.realise(s.sps).toString().equals(realiser.realise(sent.sps).toString())) 
                duplicate = true; 
         }
         if (!duplicate) sentences.add(sent);
    }

    public static void main(String args[]) {
        String query2 = "PREFIX res: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT DISTINCT ?height "
                + "WHERE { res:Claudia_Schiffer dbo:height ?height . "
                + "FILTER(\"1.0e6\"^^<http://www.w3.org/2001/XMLSchema#double> <= ?height)}";

        String query = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "SELECT ?uri ?x "
                + "WHERE { "
                + "{res:Abraham_Lincoln dbo:deathPlace ?uri} "
                + "UNION {res:Abraham_Lincoln dbo:birthPlace ?uri} . "
                + "?uri rdf:type dbo:Place. "
                + "FILTER regex(?uri, \"France\").  "
                + "FILTER (lang(?uri) = 'en')"
                + "OPTIONAL { ?uri dbo:Name ?x }. "
                + "}";
        String query3 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX yago: <http://dbpedia.org/class/yago/> "
                + "SELECT COUNT(DISTINCT ?uri) "
                //+ "SELECT ?uri "
                + "WHERE { ?uri rdf:type yago:EuropeanCountries . ?uri dbo:governmentType ?govern . "
                + "FILTER regex(?govern,'monarchy') . "
                //+ "FILTER (!BOUND(?date))"
                + "}";
        String query4 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE { ?cave rdf:type dbo:Cave . "
                + "?cave dbo:location ?uri . "
                + "?uri rdf:type dbo:Country . "
                + "OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') } }"
                + " GROUP BY ?uri ?string "
                + "HAVING (COUNT(?cave) > 2)";
        String query5 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "SELECT DISTINCT ?uri "
                + "WHERE { ?cave rdf:type dbo:Cave . "
                + "?cave dbo:location ?uri . "
                + "?uri rdf:type dbo:Country . "
                + "?uri dbo:writer ?y . FILTER(!BOUND(?cave))"
                + "?cave dbo:location ?x } ";

        String query6 = "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "PREFIX dbp: <http://dbpedia.org/property/> "
                + "PREFIX res: <http://dbpedia.org/resource/> "
                + "ASK WHERE { { res:Batman_Begins dbo:starring res:Christian_Bale . } "
                + "UNION { res:Batman_Begins dbp:starring res:Christian_Bale . } }";

        String query7 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/>"
                + "PREFIX res: <http://dbpedia.org/resource/>"
                + "PREFIX yago: <http://dbpedia.org/class/yago/>"
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE { "
                + "	?uri rdf:type yago:RussianCosmonauts."
                + "        ?uri rdf:type yago:FemaleAstronauts ."
                + "OPTIONAL { ?uri rdfs:label ?string. FILTER (lang(?string) = 'en') }"
                + "}";

        String querya = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX  res:  <http://dbpedia.org/resource/> "
                + "PREFIX  dbo:  <http://dbpedia.org/ontology/> "
                + "PREFIX  dbp:  <http://dbpedia.org/property/> "
                + "PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + " "
                + "ASK "
                + "WHERE "
                + "  {   { res:Batman_Begins dbo:starring res:Christian_Bale } "
                + "    UNION "
                + "      { res:Batman_Begins dbp:starring res:Christian_Bale } "
                + "  }";
        String query8 = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX  dbo:  <http://dbpedia.org/ontology/> "
                + "PREFIX  dbp:  <http://dbpedia.org/property/> "
                + "PREFIX  dbp:  <http://dbpedia.org/ontology/> "
                + "PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "SELECT DISTINCT  ?uri ?string "
                + "WHERE { ?uri rdf:type dbo:Country . "
                + "{?uri dbp:birthPlace ?language} UNION {?union dbo:birthPlace ?language} "
                + "OPTIONAL { ?uri rdfs:label ?string "
                + "FILTER ( lang(?string) = \'en\' )} } "
                + "GROUP BY ?uri ?string "
                + "ORDER BY ?language "
                + "LIMIT 10 OFFSET 20";

        String query9 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1/> "
                + "PREFIX mo: <http://purl.org/ontology/mo/> "
                + "SELECT DISTINCT ?artisttype "
                + "WHERE {"
                + "?artist foaf:name 'Liz Story'."
                + "?artist rdf:type ?artisttype ."
                + "FILTER (?artisttype != mo:MusicArtist)"
                + "}";

        String query10 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                + "PREFIX yago: <http://dbpedia.org/class/yago/>"
                + "PREFIX dbo: <http://dbpedia.org/ontology/>"
                + "PREFIX dbp: <http://dbpedia.org/property/>"
                + "PREFIX res: <http://dbpedia.org/resource/>"
                + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
                + "SELECT DISTINCT ?uri ?string "
                + "WHERE {"
                + "?uri rdf:type dbo:Person ."
                + "{ ?uri rdf:type yago:PresidentsOfTheUnitedStates. } "
                + "UNION "
                + "{ ?uri rdf:type dbo:President."
                + "?uri dbp:title res:President_of_the_United_States. }"
                + "?uri rdfs:label ?string."
                + "FILTER (lang(?string) = 'en' && !regex(?string,'Presidency','i') && !regex(?string,'and the')) ."
                + "}";

        query8 = "SELECT * WHERE {" +
        		"?s <http://dbpedia.org/ontology/PopulatedPlace/areaTotal> ?lit. " +
        		"FILTER(?lit = \"1.0\"^^<" + "http://dbpedia.org/datatypes/squareKilometre"/*XSD.integer.getURI()*/ + ">)}";
        
        query10 = "PREFIX  res:  <http://dbpedia.org/resource/> " +
        		"PREFIX  dbo:  <http://dbpedia.org/ontology/> " +
        		"PREFIX  yago: <http://dbpedia.org/class/yago/> " +
        		"PREFIX  dbp:  <http://dbpedia.org/property/> " +
        		"PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
        		"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
        		"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        		"SELECT DISTINCT ?person WHERE {" +
        		" ?person rdf:type dbo:Person.  " +
        		"       { ?person dbo:occupation res:Writer. } " +
        		"       UNION" +
        		"        { ?person dbo:occupation res:Surfing. }" +
        		"        ?person dbo:birthDate ?date." +
        		"        FILTER(?date > \"1950\"^^xsd:date) ." +
        		"        OPTIONAL {?person rdfs:label ?string" +
        		"        FILTER ( lang(?string) = \"en\" ) } }";
        
//        query8 = "SELECT * WHERE {" +
//        		"?s <http://dbpedia.org/ontology/PopulatedPlace/areaTotal> \"12\"^^<http://dbpedia.org/datatypes/squareKilometre>.} ";

        try {
            SparqlEndpoint ep = new SparqlEndpoint(new URL("http://greententacle.techfak.uni-bielefeld.de:5171/sparql"));
            SimpleNLGwithPostprocessing snlg = new SimpleNLGwithPostprocessing(ep);
            Query sparqlQuery = QueryFactory.create(query10, Syntax.syntaxARQ);
            System.out.println("Simple NLG: Query is distinct = " + sparqlQuery.isDistinct());
            System.out.println("Simple NLG: " + snlg.getNLR(sparqlQuery));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
