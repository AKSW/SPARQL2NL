
package org.aksw.sparql2nl.smooth_nlg;

/**
 *
 * @author christina
 */
public class OrderBy {
    
    String var;
    int direction;
    long offset;
    long limit;
    
    public OrderBy(String v,int d,long o,long l) {
        var = v;
        direction = d;
        offset = o;
        limit = l;
    }
    public OrderBy(String v,int d) {
        var = v;
        direction = d;
        offset = 0;
        limit = 0;
    }
}
