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

package org.didelphis.genetics.learning;

import io.jenetics.Chromosome;
import io.jenetics.DoubleChromosome;
import io.jenetics.DoubleGene;
import io.jenetics.EliteSelector;
import io.jenetics.GaussianMutator;
import io.jenetics.Gene;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.didelphis.genetics.alignment.Alignment;
import org.didelphis.genetics.alignment.AlignmentResult;
import org.didelphis.genetics.alignment.algorithm.AlignmentAlgorithm;
import org.didelphis.genetics.alignment.algorithm.NeedlemanWunschAlgorithm;
import org.didelphis.genetics.alignment.algorithm.SimpleLevensteinDistance;
import org.didelphis.genetics.alignment.algorithm.optimization.BaseOptimization;
import org.didelphis.genetics.alignment.operators.SequenceComparator;
import org.didelphis.genetics.alignment.operators.comparators.LinearWeightComparator;
import org.didelphis.genetics.alignment.operators.comparators.MatrixComparator;
import org.didelphis.genetics.alignment.operators.comparators.SparseMatrixComparator;
import org.didelphis.genetics.alignment.operators.gap.ConvexGapPenalty;
import org.didelphis.io.DiskFileHandler;
import org.didelphis.io.FileHandler;
import org.didelphis.language.automata.Regex;
import org.didelphis.language.parsing.FormatterMode;
import org.didelphis.language.phonetic.SequenceFactory;
import org.didelphis.language.phonetic.features.FeatureType;
import org.didelphis.language.phonetic.features.IntegerFeature;
import org.didelphis.language.phonetic.model.FeatureMapping;
import org.didelphis.language.phonetic.model.FeatureModel;
import org.didelphis.language.phonetic.model.FeatureModelLoader;
import org.didelphis.language.phonetic.model.FeatureSpecification;
import org.didelphis.language.phonetic.segments.Segment;
import org.didelphis.language.phonetic.sequences.BasicSequence;
import org.didelphis.language.phonetic.sequences.Sequence;
import org.didelphis.structures.maps.GeneralTwoKeyMap;
import org.didelphis.structures.maps.interfaces.TwoKeyMap;
import org.didelphis.structures.tables.AbstractTable;
import org.didelphis.structures.tables.RectangularTable;
import org.didelphis.structures.tables.Table;
import org.didelphis.structures.tuples.Twin;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.jenetics.engine.EvolutionResult.toBestPhenotype;
import static io.jenetics.engine.Limits.bySteadyFitness;

/**
 * Class {@code OptimizationEngine}
 *
 * @author Samantha Fiona McCabe Date: 2017-08-01
 * @since 0.1.0
 */
@ToString
@EqualsAndHashCode
@FieldDefaults (level = AccessLevel.PRIVATE, makeFinal = true)
public final class Calibrator<T, P> {

	private static final NumberFormat DOUBLE_FORMAT      = new DecimalFormat("0.000");
	private static final NumberFormat DOUBLE_FORMAT_LONG = new DecimalFormat(" 0.00000;-0.00000");

	private static final Regex PIPE     = new Regex("\\s+\\|\\s+");
	private static final Regex NEWLINES = new Regex("\r\n|\n|\r");
	private static final Regex BLOCK    = new Regex("(\r\n\r\n)|(\n\n)|(\r\r)");
	private static final Regex COMMENT  = new Regex("%[^\n\r]*(\r\n|\n|\r)");

	private static final double FIXED_WEIGHT   = 1.00;
	private static final int    FIXED_POSITION = 1;

	FileHandler handler;
	Sequence<T> gap;
	SequenceFactory<T> factory;
	int extraParams;
	List<Twin<Integer>> correlatedFeatures;
	FeatureModel<T> featureModel;
	Map<String, List<List<Alignment<T>>>> trainingData;

	public static void main(String[] args) {
		// Basic case for a static model
		// 1. Load Data
		// 2. Load Model
		// 3. Generate Algorithm
		// 4. Align Data
		// 5. Score Data

		// -----------------------------------------------
		FeatureType<Integer> type = IntegerFeature.INSTANCE;
		String modelPath = "/projects/data/AT_extended_x.model";
		FormatterMode mode = FormatterMode.INTELLIGENT;

		FileHandler handler = new DiskFileHandler("UTF-8");

		SequenceFactory<Integer> factory = loadFactory(
				modelPath,
				handler,
				type,
				mode
		);

		Sequence<Integer> gap = factory.toSequence("░");

		List<Twin<String>> fCorrelation = new ArrayList<>();
		add(fCorrelation, "con", "son");
		add(fCorrelation, "con", "cnt");
		add(fCorrelation, "son", "cnt");
		add(fCorrelation, "lat", "nas");
		add(fCorrelation, "vce", "son");

		Calibrator<Integer, Phenotype<DoubleGene, Double>> calibrator
				= new Calibrator<>(handler, gap, factory, 2, fCorrelation);

		calibrator.addSDM("/projects/data/training/training_synthetic.sdm");
		calibrator.addSDM("/projects/data/training/CHE_BCB.sdm");

		//		calibrator.addFile("/projects/data/training/training_synthetic.csv");
//		calibrator.addFile("D:/git/data/training/training_CHM-TND_aligned.csv");
//		calibrator.addFile("D:/git/data/training/training_ING-CHE_aligned.csv");

		Phenotype<DoubleGene, Double> phenotype = calibrator.optimize();
		Genotype<DoubleGene> genotype = phenotype.getGenotype();

		AlignmentAlgorithm<Integer> algorithm = calibrator.toAlgorithm(genotype);

		long timestamp = System.currentTimeMillis();
		for (String path : calibrator.trainingData.keySet()) {
			String fileName = path.replaceAll("\\.csv$", "_" + timestamp);
			writeBestAlignments(path, fileName, calibrator, algorithm);
		}

		System.out.printf("%s | %s%n",
				DOUBLE_FORMAT_LONG.format(phenotype.getFitness()).trim(),
				toParameterString(phenotype, DOUBLE_FORMAT_LONG)
		);
	}

	public Calibrator(
			FileHandler handler,
			Sequence<T> gap,
			SequenceFactory<T> factory,
			int extraParams,
			Collection<Twin<String>> correlatedFeatures
	) {
		this.handler = handler;
		this.gap = gap;
		this.factory = factory;
		this.extraParams = extraParams;
		this.correlatedFeatures = toIndices(factory, correlatedFeatures);
		trainingData = new LinkedHashMap<>();
		featureModel = factory.getFeatureMapping().getFeatureModel();
	}

	private Phenotype<DoubleGene, Double> optimize() {
		FeatureMapping<T> featureMapping = factory.getFeatureMapping();
		FeatureSpecification specification = featureMapping.getSpecification();

		int size = Double.isNaN(FIXED_WEIGHT)
				? specification.size() : specification.size() - 1;

		Engine<DoubleGene, Double> engine = Engine.builder(this::fitness,
				DoubleChromosome.of( -2,  2 , extraParams),
				DoubleChromosome.of(  0,  1, size)
//				DoubleChromosome.of(-10, 10, correlatedFeatures.size())
//				DoubleChromosome.of( -5,  5, ((size * size)-size)/2)

		)
				.maximizing()
				.populationSize(2000)
//				.maximalPhenotypeAge(20)
//				.survivorsFraction(0.8)
//				.offspringSize(3)
//				.selector(new MonteCarloSelector<>())
//				.selector(new BoltzmannSelector<>(0.5))
//				.selector(new StochasticUniversalSelector<>())
				.selector(new EliteSelector<>())
				.alterers(new GaussianMutator<>())
				.build();

		return engine.stream()
//				.limit(byFixedGeneration(100))
//				.limit(byFitnessConvergence(shortFilterSize, longFilterSize, epsilon))
				.limit(bySteadyFitness(100))
//				.peek(EvolutionStatistics.ofNumber())
				.peek(Calibrator::print)
				.collect(toBestPhenotype());
	}

	/* TODO: fields needed for an instance
	 *  - Model Path
	 *  -? Formatter Mode
	 *  - gap symbol
	 *  - training file or files
	 *  - Selector mode
	 *  - Population size
	 *  - Number of generations
	 */

	private void addSDM(String filePath) {
		String fileData;
		try {
			fileData = handler.read(filePath);
		} catch (IOException e) {
			return;
		}

		List<List<Alignment<T>>> alignmentSet = new ArrayList<>();

		int size = 0;

		// Each block should represent a single alignment or set of equivalent
		// alignments:
		// a a b | a a b
		// a - b | - a b
		// These are not *necessarily* equivalent in all cases - under a global
		// alignment they could be equivalent, but would not be so under a
		// local alignment where gaps at the beginning and end of a sequence do
		// not incur a cost
		for (String block : BLOCK.split(fileData)) {
			block = COMMENT.replace(block,"").trim();

			if (block.isEmpty()) continue;

			// The first block should contain the language headers and however
			// many headers there are should be set as the correct number later.
			// Any subsequence block with too many or too few headers will be
			// skipped and logged.
			if (size == 0) {
				size = NEWLINES.split(block).size();
				continue;
			}

			int blockWidth = 0;
			List<List<String>> lists = new ArrayList<>();
			for (String line : NEWLINES.split(block)) {
				List<String> list = PIPE.split(line);
				if (blockWidth == 0) {
					blockWidth = list.size();
				}
				lists.add(list);
			}

			List<Alignment<T>> alignments = new ArrayList<>();
			for (int i = 0; i < blockWidth; i++) {
				Collection<String> strings = new ArrayList<>();
				for (int j = 0; j <  size; j++) {
					String item = lists.get(j).get(i).trim();
					if (!item.startsWith("#")) {
						item = "# " + item;
					}
					strings.add(item);
				}
				alignments.add(toAlignment(strings, factory));
			}
			alignmentSet.add(alignments);
		}
		trainingData.put(filePath, alignmentSet);
	}

	private <G extends Gene<Double, G>> double fitness(Genotype<G> genotype) {
		Set<String> filePaths = trainingData.keySet();
		AlignmentAlgorithm<T> algorithm = toAlgorithm(genotype);
		return evaluate(filePaths, algorithm);
	}

	@NonNull
	private <G extends Gene<Double, G>> AlignmentAlgorithm<T> toAlgorithm(
			Genotype<G> genotype
	) {
		List<Double> listA = toList(genotype, 0);
		List<Double> listB = toList(genotype, 1);

		double openPenalty = listA.get(0);
		double growPenalty = listA.get(1);

		SequenceComparator<T> comparator;
		if (genotype.length() == 3) {
			List<Double> listC = toList(genotype, 2);
			comparator = getSparseComparator(listB, toSparseWeights(listC));
		} else {
			comparator = getFlatComparator(listB);
		}

		return new NeedlemanWunschAlgorithm<>(
				BaseOptimization.MIN,
				comparator,
				new ConvexGapPenalty<>(gap, openPenalty, growPenalty),
				factory
		);
	}

	@NonNull
	private TwoKeyMap<Integer, Integer, Double> toSparseWeights(List<Double> chromosome) {
		TwoKeyMap<Integer, Integer, Double> correlates = new GeneralTwoKeyMap<>();
		for (int i = 0; i < correlatedFeatures.size(); i++) {
			Twin<Integer> feature = correlatedFeatures.get(i);
			Double aDouble = chromosome.get(i);
			correlates.put(feature.getLeft(), feature.getRight(), aDouble);
		}
		return correlates;
	}

	@NonNull
	private SequenceComparator<T> getFlatComparator(List<Double> weights) {
		if (!Double.isNaN(FIXED_WEIGHT)) {
			weights.add(FIXED_POSITION, FIXED_WEIGHT);
		}
		FeatureType<T> type = featureModel.getFeatureType();
		return new LinearWeightComparator<>(type, weights);
	}

	@NonNull
	private SequenceComparator<T> getSparseComparator(
			List<Double> weights,
			TwoKeyMap<Integer, Integer, Double> sparseWeights
	) {
		if (!Double.isNaN(FIXED_WEIGHT)) {
			weights.add(FIXED_POSITION, FIXED_WEIGHT);
		}
		FeatureType<T> type = featureModel.getFeatureType();
		return new SparseMatrixComparator<>(type, weights, sparseWeights);
	}

	@NonNull
	private SequenceComparator<T> getMatrixComparator(
			List<Double> chromosomeB, List<Double> chromosomeC
	) {
		int size = chromosomeB.size();
		Table<Double> table = new RectangularTable<>(0.0, size, size);
		int x = 0;
		for (int row = 1; row < table.rows(); row++) {
			for (int col = 0; col < row; col++) {
				table.set(row, col, chromosomeC.get(x));
				x++;
			}
		}
		for (int i = 0; i < table.rows(); i++) {
			table.set(i, i, chromosomeB.get(i));
		}
		FeatureType<T> type = featureModel.getFeatureType();
		return new MatrixComparator<>(type, table);
	}

	private double evaluate(
			Iterable<String> paths,
			AlignmentAlgorithm<T> algorithm
	) {
		double correct = 0.0;
		double total = trainingData.values()
				.stream()
				.mapToDouble(List::size)
				.sum();

//		double total = 0.0;
//
//		SimpleLevensteinDistance<T> levenstein
//				= new SimpleLevensteinDistance<>();

		for (String path : paths) {
			List<List<Alignment<T>>> alignmentGroup = trainingData.get(path);
			for (List<Alignment<T>> alignments : alignmentGroup) {
				// Only retrieve the first alignment to create the sequences;
				// Any second entry that exists should create the same sequence
				Alignment<T> baseAlignment = alignments.get(0);
				if (baseAlignment.size() < 2) {
					continue;
				}
				List<Sequence<T>> sequences = getSequences(baseAlignment);

//				Alignment<T> tAlignment = result.getAlignments().get(0);
//				int columns = tAlignment.columns();
//				total += columns;
//
//				if (matches(alignments, result)) {
//					correct += columns;
//				} else {
//					int distance = alignments.stream()
//							.mapToInt(a1 -> levenstein.distance(a1, tAlignment))
//							.min()
//							.orElse(0);
//					correct += columns - distance;
//				}

				AlignmentResult<T> result = algorithm.apply(sequences.get(0), sequences.get(1));
				if (matches(alignments, result)) {
					correct++;
				}
			}
		}

		return total == 0.0 ? 0.0 : correct / total;
	}

	@NonNull
	private List<Sequence<T>> getSequences(Alignment<T> alignment) {
		List<Sequence<T>> sequences = new ArrayList<>();
		for (int i = 0; i < alignment.rows(); i++) {
			List<Segment<T>> list = alignment.getRow(i)
					.stream()
					.filter(segment -> !segment.equals(gap.get(0)))
					.collect(Collectors.toList());
			sequences.add(new BasicSequence<>(list, featureModel));
		}
		return sequences;
	}

	private static <P> void writeBestAlignments(
			String path,
			String fileName,
			Calibrator<Integer, P> calibrator,
			AlignmentAlgorithm<Integer> algorithm
	) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName + ".csv"))) {
			writer.write("Correct,Left,Right,Output Left, Output Right\n");
			List<List<Alignment<Integer>>> alignmentGroup = calibrator.trainingData.get(path);
			for (List<Alignment<Integer>> alignments : alignmentGroup) {
				// Only retrieve the first alignment to create the sequences;
				// Any second entry that exists should create the same sequence
				Alignment<Integer> baseAlignment = alignments.get(0);

				List<CharSequence> charSequences = baseAlignment.buildPrettyAlignments();
				if (charSequences.size() != 2) {
					continue;
				}

				List<Sequence<Integer>> sequences = calibrator.getSequences(baseAlignment);
				AlignmentResult<Integer> result = algorithm.apply(sequences.get(0), sequences.get(1));
				writer.write(matches(alignments, result) ? "1," : "0,");
				writer.write(charSequences.get(0)+","+charSequences.get(1)+",");
				List<CharSequence> list = result.getAlignments()
						.get(0)
						.buildPrettyAlignments();
				for (CharSequence sequence : list) {
					writer.write(sequence+",");
				}
				writer.write("\""+result.getTable().formattedTable()+"\"");
				writer.write("\n");

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static <T> boolean matches(
			Collection<Alignment<T>> alignments, AlignmentResult<T> result
	) {
		List<Alignment<T>> list = result.getAlignments();
		return alignments.stream().anyMatch(list::contains);
	}

	private static void add(
			Collection<? super Twin<String>> twins,
			String left,
			String right
	) {
		twins.add(new Twin<>(left, right));
	}

	private static <T> List<Twin<Integer>> toIndices(
			SequenceFactory<T> factory,
			Collection<? extends Twin<String>> correlatedFeatures
	) {
		FeatureMapping<T> featureMapping = factory.getFeatureMapping();
		FeatureSpecification specification = featureMapping.getSpecification();
		Map<String, Integer> featureIndices = specification.getFeatureIndices();
		return correlatedFeatures.stream().map(twin -> {
			String left = twin.getLeft();
			String right = twin.getRight();
			int indexLeft = featureIndices.getOrDefault(left, -1);
			int indexRight = featureIndices.getOrDefault(right, -1);
			return new Twin<>(indexLeft, indexRight);
		}).collect(Collectors.toList());
	}

	private static void print(EvolutionResult<DoubleGene, Double> result) {
		Phenotype<DoubleGene, Double> bestPhenotype = result.getBestPhenotype();
		String join = toParameterString(bestPhenotype, DOUBLE_FORMAT);

		System.out.printf(
				"%d (%d) %s : %s -> %s%n",
				result.getGeneration(),
				result.getPopulation().size(),
				DOUBLE_FORMAT.format(result.getWorstFitness()),
				DOUBLE_FORMAT.format(result.getBestFitness()),
				join
		);
	}

	@NonNull
	private static String toParameterString(Phenotype<DoubleGene, Double> best, NumberFormat format) {
		Genotype<DoubleGene> genotype = best.getGenotype();
		Collection<String> parameterGroups = new ArrayList<>();
		for (int i = 0; i < genotype.length(); i++) {
			parameterGroups.add(formatList(toList(genotype, i), format));
		}
		return String.join(" | ", parameterGroups);
	}

	private static String formatList(
			Collection<? extends Number> collection, NumberFormat format
	) {
		return collection.stream()
				.map(format::format)
				.collect(Collectors.joining(" "));
	}

	@NonNull
	private static <G extends Gene<Double, G>> List<Double> toList(
			@NonNull Genotype<G> genotype, int i
	) {
		Chromosome<G> chromosome = genotype.get(i);
		return chromosome.stream()
				.map(Gene::getAllele)
				.collect(Collectors.toList());
	}

	private static @NonNull <T> Alignment<T> toAlignment(
			@NonNull Iterable<String> list, @NonNull SequenceFactory<T> factory
	) {
		FeatureModel<T> model = factory.getFeatureMapping().getFeatureModel();
		List<Sequence<T>> sequences = toSequences(list, factory);
		return new Alignment<>(sequences, model);
	}

	private static @NonNull <T> List<Sequence<T>> toSequences(
			@NonNull Iterable<String> list, @NonNull SequenceFactory<T> factory
	) {
		List<Sequence<T>> sequences = new ArrayList<>();
		FeatureModel<T> model = factory.getFeatureMapping().getFeatureModel();
		for (String string : list) {
			Sequence<T> sequence = new BasicSequence<>(model);
			for (String s : string.split("\\s+")) {
				sequence.add(factory.toSegment(s));
			}
			sequences.add(sequence);
		}
		return sequences;
	}

	private static <T> SequenceFactory<T> loadFactory(
			String path,
			FileHandler handler,
			FeatureType<T> type,
			FormatterMode mode
	) {
		FeatureModelLoader<T> loader = new FeatureModelLoader<>(type,
				handler,
				path
		);
		return new SequenceFactory<>(loader.getFeatureMapping(), mode);
	}
}
