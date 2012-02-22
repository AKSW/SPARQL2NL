package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.Stack;

import simplenlg.features.Feature;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.sparql.expr.E_Bound;
import com.hp.hpl.jena.sparql.expr.E_Equals;
import com.hp.hpl.jena.sparql.expr.E_GreaterThan;
import com.hp.hpl.jena.sparql.expr.E_GreaterThanOrEqual;
import com.hp.hpl.jena.sparql.expr.E_Lang;
import com.hp.hpl.jena.sparql.expr.E_LessThan;
import com.hp.hpl.jena.sparql.expr.E_LessThanOrEqual;
import com.hp.hpl.jena.sparql.expr.E_LogicalNot;
import com.hp.hpl.jena.sparql.expr.E_NotEquals;
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

public class FilterExpressionConverter implements ExprVisitor{
	
	private NLGFactory nlgFactory;
	
	private boolean negated = false;
	
	private Stack<NLGElement> stack;
	
	public FilterExpressionConverter() {
		nlgFactory = new NLGFactory(Lexicon.getDefaultLexicon());
	}
	
	public NLGElement convert(Expr expr){
		startVisit();
		expr.visit(this);
		NLGElement element = stack.pop();
		if(negated){
			element.setFeature(Feature.NEGATED, true);
		}
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
		if(func instanceof E_LogicalNot){
			negated = true;
			func.getArg().visit(this);
		} else {
			SPhraseSpec phrase = nlgFactory.createClause();
			if(func instanceof E_Bound){
				phrase.setSubject(convert(func.getArg()));
				phrase.setVerb("exist");
			}
			stack.push(phrase);
		}
		
	}

	@Override
	public void visit(ExprFunction2 func) {
		SPhraseSpec phrase = nlgFactory.createClause();
		Expr left = func.getArg1();
        Expr right = func.getArg2();

        //invert if right is variable or aggregation and left side not 
        boolean inverted = false;
        if (!left.isVariable() && (right.isVariable() || right instanceof ExprAggregator)) {
            Expr tmp = left;
            left = right;
            right = tmp;
            inverted = true;
        }

        //handle subject
        left.visit(this);
        NLGElement subject = stack.pop();
        phrase.setSubject(subject);
        
        //handle object
        right.visit(this);
        NLGElement object = stack.pop();
        phrase.setObject(object);
        
        //handle verb resp. predicate
        String verb = null;
        if (func instanceof E_GreaterThan) {
            if (inverted) {
                verb = "be less than";
            } else {
                verb = "be greater than";
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
        	verb = "be equal to";
        } else if (func instanceof E_NotEquals) {
        	verb = "be equal to";
            phrase.setFeature(Feature.NEGATED, true);
        }
        phrase.setVerb(verb);
	}

	@Override
	public void visit(ExprFunction3 func) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit(ExprFunctionN func) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit(ExprFunctionOp funcOp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit(NodeValue nv) {
	
		
	}

	@Override
	public void visit(ExprVar nv) {
		NLGElement element = nlgFactory.createNounPhrase(nv.toString());
		stack.push(element);
	}

	@Override
	public void visit(ExprAggregator eAgg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finishVisit() {
		// TODO Auto-generated method stub
		
	}

	public static void main(String[] args) {
		NLGFactory nlgFactory = new NLGFactory(Lexicon.getDefaultLexicon());
		SPhraseSpec s = nlgFactory.createClause("the book", "be better than", "the computer");
		Realiser r = new Realiser(Lexicon.getDefaultLexicon());
		System.out.println(r.realise(s));
	}
}
