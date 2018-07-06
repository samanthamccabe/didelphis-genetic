package org.didelphis.genetics.alignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import org.didelphis.genetics.alignment.algorithm.AlignmentAlgorithm;
import org.didelphis.genetics.alignment.algorithm.BaseOptimization;
import org.didelphis.genetics.alignment.algorithm.NeedlemanWunschAlgorithm;
import org.didelphis.genetics.alignment.common.StringTransformer;
import org.didelphis.genetics.alignment.common.Utilities;
import org.didelphis.genetics.alignment.correspondences.Context;
import org.didelphis.genetics.alignment.correspondences.ContextPair;
import org.didelphis.genetics.alignment.correspondences.PairCorrespondenceSet;
import org.didelphis.genetics.alignment.operators.Comparator;
import org.didelphis.genetics.alignment.operators.comparators.LinearWeightComparator;
import org.didelphis.genetics.alignment.operators.gap.ConvexGapPenalty;
import org.didelphis.genetics.alignment.operators.gap.GapPenalty;
import org.didelphis.io.DiskFileHandler;
import org.didelphis.io.FileHandler;
import org.didelphis.language.parsing.FormatterMode;
import org.didelphis.language.phonetic.SequenceFactory;
import org.didelphis.language.phonetic.features.FeatureType;
import org.didelphis.language.phonetic.features.IntegerFeature;
import org.didelphis.language.phonetic.model.FeatureMapping;
import org.didelphis.language.phonetic.model.FeatureModelLoader;
import org.didelphis.language.phonetic.segments.Segment;
import org.didelphis.language.phonetic.sequences.BasicSequence;
import org.didelphis.language.phonetic.sequences.Sequence;
import org.didelphis.structures.Suppliers;
import org.didelphis.structures.maps.GeneralMultiMap;
import org.didelphis.structures.maps.interfaces.MultiMap;
import org.didelphis.structures.tables.ColumnTable;
import org.didelphis.structures.tuples.Tuple;
import org.didelphis.utilities.Logger;
import org.didelphis.utilities.Splitter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.LITERAL;
import static java.util.regex.Pattern.compile;

@UtilityClass
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public final class Main {

	Logger LOGGER = Logger.create(Main.class);
	Pattern EXTENSION_PATTERN = compile("\\.[^.]*?$");
	Pattern HYPHEN = compile("-");
	Pattern WHITESPACE = compile("(\n|\r\n?|\\s)+");
	Pattern HASH = compile("#", LITERAL);
	Pattern ZERO = compile("0");

	ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	static {
		OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
	}
	
	FileHandler HANDLER = new DiskFileHandler("UTF-8");
	

	private static String readConfigString(String key, JsonNode configNode) {
		String pathFieldName = key + "_path";
		if (configNode.has(pathFieldName)) {
			String path = configNode.get(pathFieldName).asText();
			return String.valueOf(HANDLER.read(path));
		} else if (configNode.has(key)) {
			JsonNode jsonNode = configNode.get(key);
			return jsonNode.asText();
		} else {
			throw new IllegalArgumentException("Configuration item "
					+ key
					+ " and "
					+ pathFieldName
					+ " not found");
		}
	}

	private static List<String> readConfigArray(String key, JsonNode configNode) {
		String pathFieldName = key + "_path";
		if (configNode.has(pathFieldName)) {
			String path = configNode.get(pathFieldName).asText();
			String value = String.valueOf(HANDLER.read(path));
			return Splitter.lines(value);
		} else if (configNode.has(key)) {
			JsonNode jsonNode = configNode.get(key);
			List<String> list = new ArrayList<>();
			for (JsonNode node : jsonNode) {
				list.add(node.asText(""));
			}
			return list;
		} else {
			throw new IllegalArgumentException("Configuration item "
					+ key
					+ " and "
					+ pathFieldName
					+ " not found");
		}
	}
	
	/**
	 * TODO: Rehab plan:
	 *    args:
	 *      --model
	 *      --weights
	 *      --transformer
	 *      --input file path
	 *      --fields fields from --input to read
	 *      --operations
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		if (args.length == 0) {
			LOGGER.error("You must provide a JSON configuration");
			System.exit(-1);
		}

		String configPath = args[0];

		String basePath = configPath.contains("/") 
				? configPath.replaceAll("/[^/]+$", "/") 
				: "";
		
		String configData = String.valueOf(HANDLER.read(configPath));

		// Read Configuration
		JsonNode configNode = OBJECT_MAPPER.readTree(configData);
		String modelPath   = basePath + configNode.get("model_path").asText();
		String weightsPath = basePath + configNode.get("weights_path").asText();

		List<String> transformPayload = readConfigArray(
				"transformations",
				configNode
		);
		String gapSymbol = readConfigString("gap_symbol", configNode);

		Map<File, List<String>> files = new LinkedHashMap<>();
		for (JsonNode file : configNode.get("files")) {
			String path = file.get("path").asText();
			List<String> list = readConfigArray("cols", file);
			files.put(new File(basePath + path), list);

		}
		
		Function<String, String> transformer = new StringTransformer(transformPayload);

		FeatureType<Integer> type = IntegerFeature.INSTANCE;

		FeatureModelLoader<Integer> loader = new FeatureModelLoader<>(
				type,
				HANDLER,
				modelPath
		);

		FeatureMapping<Integer> mapping = loader.getFeatureMapping();

		SequenceFactory<Integer> factory = new SequenceFactory<>(
				mapping, FormatterMode.INTELLIGENT);
		
		Sequence<Integer> gap = factory.toSequence(gapSymbol);
		GapPenalty<Integer> gapPenalty = new ConvexGapPenalty<>(gap, 0, 0);

		Comparator<Integer> comparator = readWeightsComparator(
				type, weightsPath
		);
		
//		Comparator<Integer> comparator = loadMatrixComparator(handler, factory,
//				transformer, matrixPath);

		AlignmentAlgorithm<Integer> algorithm = new NeedlemanWunschAlgorithm<>(
				comparator,
				BaseOptimization.MIN,
				gapPenalty,
				factory
		);

		Function<String,String> bFunc = new StringTransformer("^[^#] >> #$0");
		for (Entry<File, List<String>> languageEntry : files.entrySet()) {
			File tableFile = languageEntry.getKey();
			List<String> keyList = languageEntry.getValue();
			ColumnTable<String> table = Utilities.loadTable(tableFile.getPath(),
					bFunc);

			ColumnTable<Sequence<Integer>> data = Utilities.toPhoneticTable(
					table, factory, transformer, keyList);
			MultiMap<String, AlignmentResult<Integer>> alignmentMap =
					align(algorithm, keyList, data);

			List<Alignment<Integer>> standards = Utilities.toAlignments(
					Utilities.toPhoneticTable(table, factory,
							s -> ZERO.matcher(s).replaceAll(gapSymbol)
					), factory);

			String rootPath = EXTENSION_PATTERN
					.matcher(tableFile.getCanonicalPath())
					.replaceAll("/aligned/");
			writeAlignments(rootPath, alignmentMap);
			
			Map<String, PairCorrespondenceSet<Integer>> contexts = buildContexts(
					factory,
					gap.get(0),
					alignmentMap
			);
			writeContexts(contexts, rootPath);

			StringBuilder sb = new StringBuilder(standards.size() * 10);
			for (Alignment<Integer> alignment : standards) {
				alignment.removeColumn(0);
				sb.append(alignment).append('\n');
			}
			HANDLER.writeString(rootPath + "correct", sb);
		}
	}

	private static @NotNull Comparator<Integer> readWeightsComparator(
			FeatureType<Integer> type, String path
	) {
		CharSequence weightsPayload = HANDLER.read(path);
		List<Double> weights = new ArrayList<>();
		for (String string : WHITESPACE.split(weightsPayload, -1)) {
			weights.add(Double.parseDouble(string));
		}
		return new LinearWeightComparator<>(
				type, weights);
	}

	private static <T> void writeContexts(
			Map<String, PairCorrespondenceSet<T>> contexts,
			String rootPath
	) throws IOException {
		for (Entry<String, PairCorrespondenceSet<T>> entry : contexts
				.entrySet()) {
			String key = entry.getKey();

			File file = new File(rootPath + "contexts_" + key + ".tab");

			Writer writer = new BufferedWriter(new FileWriter(file));
			writer.write("L_a\tLeft\tL_p\tR_a\tRight\tR_p\n");

			entry.getValue().iterator().forEachRemaining(triple -> {
				Segment<T> left = triple.getFirstElement();
				Segment<T> right = triple.getSecondElement();
				triple.getThirdElement().forEach(pair -> {
					Context<T> lContext = pair.getLeft();
					Context<T> rContext = pair.getRight();

					Sequence<T> lA = lContext.getLeft();
					Sequence<T> lP = lContext.getRight();
					Sequence<T> rA = rContext.getLeft();
					Sequence<T> rP = rContext.getRight();

					try {
						writer.write(lA.toString());
						writer.write("\t");
						writer.write(left.toString());
						writer.write("\t");
						writer.write(lP.toString());
						writer.write("\t");
						writer.write(rA.toString());
						writer.write("\t");
						writer.write(right.toString());
						writer.write("\t");
						writer.write(rP.toString());
						writer.write("\n");
						writer.flush();
					} catch (IOException e) {
						LOGGER.error("Failed to write output", e);
					}
				});
			});
			writer.close();
		}
	}

	private static <T> void writeAlignments(String rootPath,
			Iterable<Tuple<String, Collection<AlignmentResult<T>>>> alignments) {
		ObjectMapper objectMapper = new ObjectMapper();
		for (Tuple<String, Collection<AlignmentResult<T>>> entry : alignments) {
			String key = entry.getLeft();
			StringBuilder sb1 = new StringBuilder();
			StringBuilder sb2 = new StringBuilder();
			sb1.append(HYPHEN.matcher(key).replaceAll("\t"));
			sb1.append('\n');
			for (AlignmentResult<T> result : entry.getRight()) {
				Iterator<Alignment<T>> list = result.getAlignments().iterator();
				Iterable<CharSequence> charSequences = list.hasNext()
						? list.next().buildPrettyAlignments()
						: Collections.emptyList();
				for (CharSequence sequence : charSequences) {
					String normal = Normalizer.normalize(sequence, Form.NFC);
					String str = HASH.matcher(normal)
							.replaceAll(Matcher.quoteReplacement("")).trim();
					sb1.append(str);
					sb1.append('\t');
				}
				sb1.append('\n');

				ObjectNode node = new ObjectNode(objectMapper.getNodeFactory());
				node.put("left",result.getLeft().toString());
				node.put("right",result.getRight().toString());
				List<Object> objects = new ArrayList<>();
				for (Alignment<T> alignment : result.getAlignments()) {
					objects.add(alignment.getPrettyTable().split("\n"));
				}
				List<Object> table = new ArrayList<>();
				Iterator<Collection<Double>> it = result.getTable().rowIterator();
				while (it.hasNext()) {
					table.add(it.next());
				}
				node.putPOJO("alignments",objects);
				node.putPOJO("table", table);
				try {
					sb2.append(objectMapper.writeValueAsString(node));
				} catch (JsonProcessingException e) {
					LOGGER.error("{}", e);
				}
			}

			File file1 = new File(rootPath + "alignments_" + key + ".csv");
			File file2 = new File(rootPath + "alignments_" + key + ".json");
			Path path = file1.toPath();
			try {
				Files.createDirectories(path.getParent());
			} catch (IOException e) {
				LOGGER.error("{}", e);
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file1))) {
				writer.write(sb1.toString());
			} catch (IOException e) {
				LOGGER.error("{}", e);
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file2))) {
				writer.write(sb2.toString());
			} catch (IOException e) {
				LOGGER.error("{}", e);
			}
		}
	}

	private static <T> Map<String, PairCorrespondenceSet<T>> buildContexts(
			SequenceFactory<T> factory,
			Segment<T> gap,
			MultiMap<String, AlignmentResult<T>> alignmentMap
	) {
		Map<String, PairCorrespondenceSet<T>> contexts = new HashMap<>();
		for (Tuple<String, Collection<AlignmentResult<T>>> e : alignmentMap) {

			String key = e.getLeft();

			PairCorrespondenceSet<T> set = new PairCorrespondenceSet<>();
			for (AlignmentResult<T> alignmentResult : e.getRight()) {

				List<Alignment<T>> alignments = alignmentResult.getAlignments();

				for (Alignment<T> alignment : alignments) {
					
					if (alignment.columns() > 0) {
						List<Segment<T>> left = new ArrayList<>(alignment.getRow(0));
						List<Segment<T>> right = new ArrayList<>(alignment.getRow(1));

						left.add(factory.toSegment("#"));
						right.add(factory.toSegment("#"));

						for (int i = 1; i < alignment.columns() - 1; i++) {
							Segment<T> l = left.get(i);
							Segment<T> r = right.get(i);

							if (!l.equals(r)) {
								Sequence<T> lA = lookBack(left, i, gap);
								Sequence<T> rA = lookBack(right, i, gap);

								Sequence<T> lP = lookForward(left, i, gap);
								Sequence<T> rP = lookForward(right, i, gap);

								ContextPair<T> pair = new ContextPair<>(
										new Context<>(lA, lP),
										new Context<>(rA, rP)
								);

								set.add(l, r, pair);
							}
						}
					}
				}
			}
			contexts.put(key, set);
		}
		return contexts;
	}

	@NonNull
	private static <T> MultiMap<String, AlignmentResult<T>> align(
			@NotNull AlignmentAlgorithm<T> algorithm,
			@NotNull List<String> keyList,
			@NotNull ColumnTable<Sequence<T>> data
	) {
		MultiMap<String, AlignmentResult<T>> alignmentMap =
				new GeneralMultiMap<>(new LinkedHashMap<>(), Suppliers.ofList());
		for (int i = 0; i < keyList.size(); i++) {
			String k1 = keyList.get(i);
			List<Sequence<T>> d1 = data.getColumn(k1);
			for (int j = 1; j < keyList.size() && j != i; j++) {
				String k2 = keyList.get(j);
				List<Sequence<T>> d2 = data.getColumn(k2);

				if (d1 == null || d2 == null || d1.size() != d2.size()) {
					return null;
				}

				Collection<AlignmentResult<T>> alignments = new ArrayList<>();
				Iterator<Sequence<T>> it1 = d1.iterator();
				Iterator<Sequence<T>> it2 = d2.iterator();
				while (it1.hasNext() && it2.hasNext()) {
					Sequence<T> e1 = it1.next();
					Sequence<T> e2 = it2.next();
					List<Sequence<T>> list = asList(e1, e2);
					AlignmentResult<T> result = algorithm.apply(list);
					alignments.add(result);
				}
				alignmentMap.addAll(k1 + '-' + k2, alignments);
			}
		}
		return alignmentMap;
	}

	private static <T> Sequence<T> lookBack(
			List<Segment<T>> segments,
			int i,
			Segment<T> gap
	) {
		List<Segment<T>> collect = segments.subList(0, i)
				.stream()
				.filter(segment -> !segment.equals(gap))
				.collect(Collectors.toList());
		return new BasicSequence<>(collect, gap.getFeatureModel());
	}

	private static <T> Sequence<T> lookForward(
			List<Segment<T>> segments,
			int i, 
			Segment<T> gap
	) {
		List<Segment<T>> collect = segments.subList(i+1, segments.size())
				.stream()
				.filter(segment -> !segment.equals(gap))
				.collect(Collectors.toList());
		return new BasicSequence<>(collect, gap.getFeatureModel());
	}
}
