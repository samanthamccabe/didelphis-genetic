package org.didelphis.genetics.alignment.correspondences;

import org.didelphis.common.structures.tuples.Tuple;

/**
 * Samantha Fiona Morrigan McCabe
 * Created: 4/15/2016
 */
public class ContextPair<T> extends Tuple<Context<T>, Context<T>> {

	public ContextPair(Context<T> left, Context<T> right) {
		super(left, right);
	}
}
