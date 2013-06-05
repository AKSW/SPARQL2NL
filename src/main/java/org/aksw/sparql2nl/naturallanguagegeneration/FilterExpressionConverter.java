package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.Stack;

import org.h2.engine.RightOwner;

import simplenlg.features.Feature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.impl.LiteralLabel;
import com.hp.hpl.jena.sparql.expr.E_Bound;
import com.hp.hpl.jena.sparql.expr.E_Cast;
import com.hp.hpl.jena.sparql.expr.E_Equals;
import com.hp.hpl.jena.sparql.expr.E_Function;
import com.hp.hpl.jena.sparql.expr.E_GreaterThan;
import com.hp.hpl.jena.sparql.expr.E_GreaterThanOrEqual;
import com.hp.hpl.jena.sparql.expr.E_Lang;
import com.hp.hpl.jena.sparql.expr.E_LessThan;
import com.hp.hpl.jena.sparql.expr.E_LessThanOrEqual;
import com.hp.hpl.jena.sparql.expr.E_LogicalAnd;
import com.hp.hpl.jena.sparql.expr.E_LogicalNot;
import com.hp.hpl.jena.sparql.expr.E_LogicalOr;
import com.hp.hpl.jena.sparql.expr.E_NotEquals;
import com.hp.hpl.jena.sparql.expr.E_Regex;
import com.hp.hpl.jena.sparql.expr.E_Str;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
import com.hp.hpl.jena.sparql.expr.ExprFunction;
import com.hp.hpl.jena.sparql.expr.ExprFunction0;
import com.hp.hpl.jena.sparql.expr.ExprFunction1;
import com.hp.hpl.jena.sparql.expr.ExprFunction2;
import com.hp.hpl.jena.sparql.expr.ExprFunction3;
import com.hp.hpl.jena.sparql.expr.ExprFunctionN;
import com.hp.hpl.jena.sparql.expr.ExprFunctionOp;
import com.hp.hpl.jena.sparql.expr.ExprVar;
import com.hp.hpl.jena.sparql.expr.ExprVisitor;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.expr.aggregate.AggAvg;
import com.hp.hpl.jena.sparql.expr.aggregate.AggCountVar;
import com.hp.hpl.jena.sparql.expr.aggregate.AggMax;
import com.hp.hpl.jena.sparql.expr.aggregate.AggMin;
import com.hp.hpl.jena.sparql.expr.aggregate.Aggregator;
import com.hp.hpl.jena.vocabulary.XSD;

public class FilterExpressionConverter implements ExprVisitor{
	
	private NLGFactory nlgFactory;
	private Realiser realiser;
	
	private Stack<NLGElement> stack;
	
	private URIConverter uriConverter;
	private LiteralConverter literalConverter;
	
	private boolean simplifyLanguageFilterConstructs = true;
	private boolean inRegex = false;
	
	public FilterExpressionConverter(URIConverter uriConverter, LiteralConverter literalConverter) {
		this.uriConverter = uriConverter;
		this.literalConverter = literalConverter;
		
		Lexicon lexicon = Lexicon.getDefaultLexicon();
		nlgFactory = new NLGFactory(lexicon);
		realiser = new Realiser(lexicon);
	}
	
	public NLGElement convert(Expr expr){
		startVisit();
		expr.visit(this);
		NLGElement element = stack.pop();
		finishVisit();
		return element;
	}

	@Override
	public void startVisit() {
		stack = new Stack<NLGElement>();
	}

	@Override
	public void visit(ExprFunction0 func) {
		
		
	}

	@Override
	public void visit(ExprFunction1 func) {
		func.getArg().visit(this);
		NLGElement subject = stack.pop();
		
		NLGElement element;
		if(func instanceof E_LogicalNot){
			subject.setFeature(Feature.NEGATED, true);
			stack.push(subject);
		} else {
			if(func instanceof E_Bound){
				element = nlgFactory.createClause();
				((SPhraseSpec) element).setSubject(subject);
				((SPhraseSpec) element).setVerb("exist");
			} else if(func instanceof E_Str){
				element = nlgFactory.createNounPhrase("the string of " + realiser.realise(subject).getRealisation());
			} else if(func instanceof E_Lang){
				String s = null;
				if(simplifyLanguageFilterConstructs){
					s = realiser.realise(subject).getRealisation();
				} else {
					s = "the language of " + realiser.realise(subject).getRealisation();
				}
				element = nlgFactory.createNounPhrase(s);
			} else {
				throw new UnsupportedOperationException(func + " is not implemented yet.");
			}
			stack.push(element);
		} 
		
	}

	@Override
	public void visit(ExprFunction2 func) {
		Expr left = func.getArg1();
        Expr right = func.getArg2();

        //invert if right is variable or aggregation and left side not 
        boolean inverted = false;
        if (!left.isVariable() && (right.isVariable() || right instanceof ExprAggregator)) {
            Expr tmp = left;
            left = right;
            right = tmp;
            if (func instanceof E_GreaterThan) {
            	func = new E_LessThan(left, right);
            } else if (func instanceof E_GreaterThanOrEqual) {
            	func = new E_LessThanOrEqual(left, right);
            } else if (func instanceof E_LessThan) {
            	func = new E_GreaterThan(left, right);
            } else if (func instanceof E_LessThanOrEqual) {
            	func = new E_GreaterThanOrEqual(left, right);
            }
//            inverted = true;
        }

        //handle left side
        left.visit(this);
        NLGElement leftElement = stack.pop();
        
        //handle right side
        right.visit(this);
        NLGElement rightElement = stack.pop();
        
        if(func instanceof E_LogicalAnd || func instanceof E_LogicalOr){
        	CoordinatedPhraseElement c = nlgFactory.createCoordinatedPhrase();
        	c.addCoordinate(leftElement);
        	c.addCoordinate(rightElement);
        	if (func instanceof E_LogicalOr){
            	c.setConjunction("or");
            }
        	stack.push(c);
        } else {
        	SPhraseSpec phrase = nlgFactory.createClause();
        	 //handle verb resp. predicate
            String verb = null;
            boolean plural = false;
            if (func instanceof E_GreaterThan) {
                if (inverted) {
                    verb = "be less than";
                } else {
                	verb = "be greater than";
                	if(right.isConstant()){
//                		System.out.println(right.getConstant().getNode());
//                		System.out.println(right.getConstant().getNode().getLiteralDatatype());
                		if(right.getConstant().getNode().getLiteralDatatype().getURI().equals(XSDDatatype.XSDdate.getURI())){
                			verb = "be later than";
                		}
                	}
                }
            } else if (func instanceof E_GreaterThanOrEqual) {
                if (inverted) {
                    verb = "be less than or equal to";
                } else {
                    verb = "be greater than or equal to";
                }
            } else if (func instanceof E_LessThan) {
                if (inverted) {
                    verb = "be greater than";
                } else {
                    verb = "be less than";
                }
            } else if (func instanceof E_LessThanOrEqual) {
                if (inverted) {
                    verb = "be greater than or equal to";
                } else {
                    verb = "be less than or equal to";
                }
            } else if(func instanceof E_Equals){
            	if(left instanceof E_Lang && simplifyLanguageFilterConstructs){
            		rightElement = nlgFactory.createNounPhrase(getLanguageForAbbreviation(realiser.realise(rightElement).getRealisation()));
                        verb = "be in";
                        plural = true;
            	} else {
                        if (realiser.realise(rightElement).toString().startsWith("?")) verb = "be the same as";
                        else verb = "be equal to";
            	}
            } else if (func instanceof E_NotEquals) {
            	if(left instanceof E_Lang && simplifyLanguageFilterConstructs){
            		verb = "be in";
            		rightElement = nlgFactory.createNounPhrase(getLanguageForAbbreviation(realiser.realise(rightElement).getRealisation()));
            	} else {
            		if (realiser.realise(rightElement).toString().startsWith("?")) verb = "be the same as";
                        else verb = "be equal to";
            	}
                phrase.setFeature(Feature.NEGATED, true);
            } 
            phrase.setSubject(leftElement);
            phrase.setObject(rightElement);
            phrase.setVerb(verb);
            if(plural){
            	phrase.setPlural(true);
            }
            stack.push(phrase);
        }
        
       
	}

	@Override
	public void visit(ExprFunction3 func) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit(ExprFunctionN func) {
		SPhraseSpec phrase = nlgFactory.createClause();
		if(func instanceof E_Regex){
			inRegex = true;
			Expr target = func.getArg(1);
			target.visit(this);
			phrase.setSubject(stack.pop());
			
			Expr pattern = func.getArg(2);
			pattern.visit(this);
			phrase.setObject(stack.pop());
			
			String adverb = "";
			Expr flags = func.getArg(3);
			if(flags != null){
				flags.visit(this);
				if(realiser.realise(stack.pop()).getRealisation().equals("\"i\"")){
					adverb += "ignorecase";
				}
				
			}
			phrase.setVerb("match " + adverb);
			stack.push(phrase);
			inRegex = false;
		} else if(func instanceof E_Function){
			ExprFunction function = func.getFunction();
			if(function.getFunctionIRI().equals(XSD.integer.getURI())){
				function.getArg(1).visit(this);
			} else {
				throw new UnsupportedOperationException(function.getFunctionIRI() + " is not implemented yet.");
			}
		} else {
			throw new UnsupportedOperationException(func + " is not implemented yet.");
		}
	}

	@Override
	public void visit(ExprFunctionOp funcOp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit(NodeValue nv) {
		String label = null;
		boolean isPlural = false;
		if(nv.isVariable()){
			label = nv.toString();
		} else if(nv.isIRI()){
			label = uriConverter.convert(nv.asNode().getURI());
		} else if(nv.isLiteral()){
			LiteralLabel lit = nv.asNode().getLiteral();
			label = literalConverter.convert(lit);
			isPlural = literalConverter.isPlural(lit) && !inRegex;
			if(inRegex){
				label = "\"" + label + "\"";
			}
		}
		NLGElement element = nlgFactory.createNounPhrase(nlgFactory.createWord(label, LexicalCategory.NOUN));
		element.setPlural(isPlural);
		stack.push(element);
	}

	@Override
	public void visit(ExprVar nv) {
		NLGElement element = nlgFactory.createNounPhrase(nv.toString());
		stack.push(element);
	}

	@Override
	public void visit(ExprAggregator eAgg) {
		Aggregator aggregator = eAgg.getAggregator();
        Expr expr = aggregator.getExpr();
        expr.visit(this);
        String s = null;
        if (aggregator instanceof AggCountVar) {
            s = "the number of ";
        } else if(aggregator instanceof AggMin){
        	s = "the minimum of ";
        } else if(aggregator instanceof AggMax){
        	s = "the maximum of ";
        } else if(aggregator instanceof AggAvg){
        	s = "the average of ";
        } else {
        	throw new UnsupportedOperationException("This aggregate function is not implemented yet." + eAgg);
        }
		NLGElement element = nlgFactory.createNounPhrase(s + realiser.realise(stack.pop()));
		stack.push(element);
	}

	@Override
	public void finishVisit() {
		// TODO Auto-generated method stub
		
	}
	
	private String getLanguageForAbbreviation(String abbreviation){
		if(abbreviation.equals("en")){
			return "English";
		} else if(abbreviation.equals("de")){
			return "German";
		}
		return null;
	}

}
