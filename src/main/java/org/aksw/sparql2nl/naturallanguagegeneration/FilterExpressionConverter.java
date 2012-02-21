package org.aksw.sparql2nl.naturallanguagegeneration;

import simplenlg.features.Feature;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.sparql.expr.E_Bound;
import com.hp.hpl.jena.sparql.expr.E_LogicalNot;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprAggregator;
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
	
	private NLGElement element;
	
	private boolean negated = false;
	
	public FilterExpressionConverter() {
		nlgFactory = new NLGFactory(Lexicon.getDefaultLexicon());
	}
	
	public NLGElement convert(Expr expr){
		startVisit();
		expr.visit(this);
		if(negated){
			element.setFeature(Feature.NEGATED, true);
		}
		finishVisit();
		return element;
	}

	@Override
	public void startVisit() {
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
			element = phrase;
		}
		
	}

	@Override
	public void visit(ExprFunction2 func) {
		element = nlgFactory.createClause();
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
		element = nlgFactory.createNounPhrase(nv.toString());
		
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
