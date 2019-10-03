/******************************************************************************
 * General components for language modeling and analysis                      *
 *                                                                            *
 * Copyright (C) 2014-2019 Samantha F McCabe                                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.     *
 *                                                                            *
 ******************************************************************************/

package org.didelphis.genetics.alignment.operators.comparators;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.didelphis.genetics.alignment.operators.SequenceComparator;
import org.didelphis.language.phonetic.features.FeatureArray;
import org.didelphis.language.phonetic.features.FeatureType;
import org.didelphis.language.phonetic.sequences.Sequence;
import org.didelphis.structures.maps.interfaces.TwoKeyMap;
import org.didelphis.structures.tuples.Triple;

import java.util.List;
import java.util.Map;

@ToString
@EqualsAndHashCode
public final class SparseMatrixComparator<T> implements SequenceComparator<T> {

	private final List<Double> weights;
	private final TwoKeyMap<Integer, Integer, Double> sparseWeights;
	private final FeatureType<? super T> type;

	public SparseMatrixComparator(
			FeatureType<? super T> type,
			List<Double> weights,
			TwoKeyMap<Integer, Integer, Double> sparseWeights
	) {
		this.weights = weights;
		this.type = type;
		this.sparseWeights = sparseWeights;
	}

	@Override
	public double apply(@NonNull Sequence<T> left, @NonNull Sequence<T> right, int i, int j) {
		double score = 0.0;
		FeatureArray<T> lFeatures = left.get(i).getFeatures();
		FeatureArray<T> rFeatures = right.get(j).getFeatures();
		for (int k = 0; k < weights.size(); k++) {
			T lF = lFeatures.get(k);
			T rF = rFeatures.get(k);
			Double d = type.difference(lF, rF);
			Double w = weights.get(k);
			score += w * d;
		}
		return score;
	}
}
