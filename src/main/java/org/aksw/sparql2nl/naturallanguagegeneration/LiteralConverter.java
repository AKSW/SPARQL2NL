package org.aksw.sparql2nl.naturallanguagegeneration;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.hp.hpl.jena.datatypes.DatatypeFormatException;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.IllegalDateTimeFieldException;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.impl.LiteralLabel;
import com.hp.hpl.jena.rdf.model.Literal;

public class LiteralConverter {

    private static final Logger logger = Logger.getLogger(LiteralConverter.class);
    private URIConverter uriConverter;
    private DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG);
    private boolean encapsulateStringLiterals = true;

    public LiteralConverter(URIConverter uriConverter) {
        this.uriConverter = uriConverter;
    }

    public String convert(Literal lit) {
        return convert(NodeFactory.createLiteral(lit.getLexicalForm(), lit.getLanguage(),
                lit.getDatatype()).getLiteral());
    }

    public String convert(LiteralLabel lit) {
        RDFDatatype dt = lit.getDatatype();

        String s = lit.getLexicalForm();
        if (dt == null) {// plain literal, i.e. omit language tag if exists
            s = lit.getLexicalForm();
            s = s.replaceAll("_", " ");
        } else {// typed literal
            if (dt instanceof XSDDatatype) {// built-in XSD datatype
            	try {
					Object value = lit.getValue();
					if (value instanceof XSDDateTime) {
						Calendar calendar = ((XSDDateTime) value).asCalendar();
						if(dt.equals(XSDDatatype.XSDgMonth)){
							s = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.UK);
						} else if(dt.equals(XSDDatatype.XSDgMonthDay)){
							s = calendar.get(Calendar.DAY_OF_MONTH) + calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.UK);
						} else if(dt.equals(XSDDatatype.XSDgYearMonth)){
							s = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.UK) + " " + calendar.get(Calendar.YEAR);
						} else {
							s = dateFormat.format(calendar.getTime());
						}
					}
				} catch (DatatypeFormatException | IllegalDateTimeFieldException e) {
					logger.error("Conversion of date literal " + lit + " failed. Reason: " + e.getMessage());
				}
            } else {// user-defined datatype
                s = lit.getLexicalForm() + " " + splitAtCamelCase(uriConverter.convert(dt.getURI(), false));
            }
        }
        return s;
    }

    public boolean isPlural(LiteralLabel lit) {
        boolean singular = false;
        double value = 0;
        try {
            value = Integer.parseInt(lit.getLexicalForm());
            singular = (value == 0d);
        } catch (NumberFormatException e) {
            try {
                value = Double.parseDouble(lit.getLexicalForm());
                singular = (value == 0d);
            } catch (NumberFormatException e1) {
            }
        }
        boolean isPlural = (lit.getDatatypeURI() != null) && !(lit.getDatatype() instanceof XSDDatatype) && !singular;
        return isPlural;
    }
    
    /**
     * Whether to encapsulate the value of string literals in ""
	 * @param encapsulateStringLiterals the encapsulateStringLiterals to set
	 */
	public void setEncapsulateStringLiterals(boolean encapsulateStringLiterals) {
		this.encapsulateStringLiterals = encapsulateStringLiterals;
	}

    private String splitAtCamelCase(String s) {
        String regex = "([a-z])([A-Z])";
        String replacement = "$1 $2";
        return s.replaceAll(regex, replacement).toLowerCase();
    }
    
    public String getMonthName(int month) {
        return new DateFormatSymbols().getMonths()[month-1];
    }

    public static void main(String[] args) {
        LiteralConverter conv = new LiteralConverter(new URIConverter(
                SparqlEndpoint.getEndpointDBpediaLiveAKSW()));
        LiteralLabel lit;// = NodeFactory.createLiteralNode("123", null,"http://dbpedia.org/datatypes/squareKilometre").getLiteral();
//        System.out.println(lit);
//        System.out.println(conv.convert(lit));

        lit = NodeFactory.createLiteral("1869-06-27", null, XSDDatatype.XSDdate).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));
        
        lit = NodeFactory.createLiteral("1914-01-01T00:00:00+02:00", null, XSDDatatype.XSDgYear).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));
        
        lit = NodeFactory.createLiteral("--04", null, XSDDatatype.XSDgMonth).getLiteral();
        System.out.println(lit + " --> " + conv.convert(lit));

    }
}
