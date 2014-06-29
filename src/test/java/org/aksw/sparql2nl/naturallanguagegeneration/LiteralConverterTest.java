/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import static org.junit.Assert.*;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.impl.LiteralLabel;

/**
 * @author Lorenz Buehmann
 *
 */
public class LiteralConverterTest {

	/**
	 * Test method for {@link org.aksw.sparql2nl.naturallanguagegeneration.LiteralConverter#convert(com.hp.hpl.jena.rdf.model.Literal)}.
	 */
	@Test
	public void testConvertDate() {
		LiteralConverter conv = new LiteralConverter(new URIConverter(
                SparqlEndpoint.getEndpointDBpediaLiveAKSW()));
        LiteralLabel lit;

        lit = NodeFactory.createLiteral("1869-06-27", null, XSDDatatype.XSDdate).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));
        
        lit = NodeFactory.createLiteral("1914-01-01T00:00:00+02:00", null, XSDDatatype.XSDgYear).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));
        
        lit = NodeFactory.createLiteral("--04", null, XSDDatatype.XSDgMonth).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));
	}

}
