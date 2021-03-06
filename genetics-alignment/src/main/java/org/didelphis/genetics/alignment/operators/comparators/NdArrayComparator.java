package org.didelphis.genetics.alignment.operators.comparators;

import org.didelphis.language.phonetic.features.FeatureArray;
import org.didelphis.language.phonetic.segments.Segment;
import org.didelphis.language.phonetic.sequences.Sequence;
import org.didelphis.genetics.alignment.operators.Comparator;
import org.jetbrains.annotations.NotNull;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Created by samantha on 8/4/16.
 */
public final class NdArrayComparator<T> implements Comparator<T> {

	private static final transient Logger LOGGER =
			getLogger(NdArrayComparator.class);

	private final INDArray weights;

	public NdArrayComparator(double[] array) {
		weights = Nd4j.create(array);
	}

	@Override
	public double apply(@NotNull Sequence<T> left, @NotNull Sequence<T> right, int i, int j) {

		INDArray lF = getNdFeatureArray(left.get(i));
		INDArray rF = getNdFeatureArray(right.get(j));

		INDArray dif = lF.sub(rF);
		// in-place element-wise multiplication
		dif.muli(weights);
		return dif.sumNumber().doubleValue();
	}

	private INDArray getNdFeatureArray(Segment<T> segment) {
		FeatureArray<T> featureArray = segment.getFeatures();
		INDArray ndArray;
		//		if (featureArray instanceof NdFeatureArray) {
		//			ndArray = ((NdFeatureArray) featureArray).getNdArray();
		//		} else {
		//			ndArray = new NdFeatureArray(featureArray).getNdArray();
		//		}
		//		return ndArray;
		return null; // TODO:
	}
}
