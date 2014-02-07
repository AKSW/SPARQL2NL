package org.aksw.assessment.question;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class PermutationsOfN {

  public static <T> List<List<T>> getSubsetsOfSizeN( List<T> set, int k ) {
    if ( k > set.size() ) {
      k = set.size();
    }
    List<List<T>> result = Lists.newArrayList();
    List<T> subset = Lists.newArrayListWithCapacity( k );
    for ( int i = 0; i < k; i++ ) {
      subset.add( null );
    }
    return processLargerSubsets( result, set, subset, 0, 0 );
  }

  private static <T> List<List<T>> processLargerSubsets( List<List<T>> result, List<T> set, List<T> subset, int subsetSize, int nextIndex ) {
    if ( subsetSize == subset.size() ) {
      result.add( ImmutableList.copyOf( subset ) );
    } else {
      for ( int j = nextIndex; j < set.size(); j++ ) {
        subset.set( subsetSize, set.get( j ) );
        processLargerSubsets( result, set, subset, subsetSize + 1, j + 1 );
      }
    }
    return result;
  }

  public static <T> Collection<List<T>> getPermutationsOfSizeN( List<T> list, int size ) {
    Collection<List<T>> all = Lists.newArrayList();
    if ( list.size() < size ) {
      size = list.size();
    }
    if ( list.size() == size ) {
      all.addAll( Collections2.permutations( list ) );
    } else {
      for ( List<T> p : getSubsetsOfSizeN( list, size ) ) {
        all.addAll( Collections2.permutations( p ) );
      }
    }
    return all;
  }
}