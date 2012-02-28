
package org.aksw.sparql2nl.smooth_nlg;

import com.hp.hpl.jena.sparql.syntax.Element;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author christina
 */
public class CardBox {
    
    List<Entity> primaries;
    List<Entity> secondaries;
    List<Element> filters;
    List<Element> optionals;
    List<OrderBy> orderBys;
    
    public CardBox(List<Entity> primes,List<Entity> secs,List<Element> fils,List<Element> opts,List<OrderBy> obs) {
        primaries = primes;
        secondaries = secs;
        filters = fils;
        optionals = opts;
        orderBys = obs;
    }
    
    public String[][] getMatrix(Entity e) {
        
        String[][] m = new String[e.properties.size()][3];
        int row = 0;
        for (Predicate p : e.properties) {
            m[row][0] = p.subject;
            m[row][1] = p.predicate;
            m[row][2] = p.object;
            row++;
        }       
        return m;
    }
    
    public Set<String> getSecondaryVars() {
        
        Set<String> out = new HashSet<String>();
        for (Entity e : secondaries) {
            out.add(e.var);
        }
        return out;
    }
    
    public void print() {
        
        System.out.println("\nPRIMARY ENTITIES:\n");
        for (Entity entity : primaries) {
            System.out.println(entity.toString());
        }
        System.out.println("\nSECONDARY ENTITIES:\n");
        for (Entity entity : secondaries) {
            System.out.println(entity.toString());
        }
        System.out.println("...");
    }
    
}
