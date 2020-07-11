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

import lombok.NonNull;
import lombok.ToString;

import org.didelphis.genetics.alignment.operators.SequenceComparator;
import org.didelphis.language.phonetic.features.FeatureArray;
import org.didelphis.language.phonetic.features.FeatureType;
import org.didelphis.language.phonetic.segments.Segment;
import org.didelphis.language.phonetic.sequences.Sequence;

import java.util.List;

@ToString
public final class ContextComparator<T> implements SequenceComparator<T> {

	private final FeatureType<? super T> type;

	private final int          fSize;
	private final List<Double> list;

	public ContextComparator(
			FeatureType<? super T> type, List<Double> list
	) {
		this.type = type;
		this.list = list;

		fSize = list.size() / 6;
	}

	@Override
	public double apply(
			@NonNull Sequence<T> left, @NonNull Sequence<T> right, int i, int j
	) {
		double score = 0.0;

		score += p(0, left, right, i-1, j);
		score += p(1, left, right, i,   j);
		score += p(2, left, right, i+1, j);
		score += p(3, left, right, i,   j-1);
		score += p(4, left, right, i,   j);
		score += p(5, left, right, i,   j+1);

		return score;
	}

	private double p(
			int block,
			@NonNull Sequence<T> left,
			@NonNull Sequence<T> right,
			int i,
			int j
	) {
		double score = 0.0;

		if (i < 0 || j < 0 || i >= left.size() || j >= right.size()) {
			return  score;
		}

		Segment<T> lS = left.get(i);
		Segment<T> rS = right.get(j);

		int start = block * fSize;
		int end   = (block + 1) * fSize;

		List<Double> weights = list.subList(start, end);

		FeatureArray<T> lFeatures = lS.getFeatures();
		FeatureArray<T> rFeatures = rS.getFeatures();
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