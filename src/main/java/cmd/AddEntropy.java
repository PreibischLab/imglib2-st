package cmd;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import analyze.Entropy;
import analyze.ExtractGeneLists;
import data.STData;
import io.SpatialDataContainer;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.DoubleType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.logging.log4j.Logger;
import util.LoggerUtil;

// In the future, this will support more methods for computing the std
@Command(name = "st-add-entropy", mixinStandardHelpOptions = true, version = "0.3.2-SNAPSHOT", description = "Spatial Transcriptomics as IMages project - add annotations to slice-dataset")
public class AddEntropy implements Callable<Void> {

	private static final Logger logger = LoggerUtil.getLogger();

	@Option(names = {"-i", "--input"}, required = true, description = "input container for which to pre-compute entropy, e.g. -i /home/ssq.n5")
	private String inputPath = null;

	@Option(names = {"-m", "--method"}, required = false, description = "method to compute gene entropy")
	private Entropy entropy = Entropy.STDEV;

	@Option(names = {"--overwrite"}, required = false, description = "overwrite existing entropy values")
	private boolean overwrite = false;

	@Option(names = {"--numThreads"}, required = false, description = "number of threads for parallel processing")
	private int numThreads = 8;

	@Override
	public Void call() throws Exception {
		if (inputPath == null) {
			logger.error("No input path defined. Stopping.");
			return null;
		}

		final ExecutorService service = Executors.newFixedThreadPool(numThreads);
		final SpatialDataContainer container = SpatialDataContainer.openExisting(inputPath, service);

		logger.info("Computing gene variability with method '{}' (might take a while)", entropy.label());

		int i = 0;
		for (final String dataset : container.getDatasets()) {
			logger.info("Computing gene variability for {} ({}/{})", dataset, ++i, container.getDatasets().size());
			if (container.hasEntropyValues(dataset, entropy) && !overwrite) {
				logger.info("Entropy values already exist for dataset '{}', skipping.", dataset);
				continue;
			}
			final STData stData = container.openDataset(dataset).readData().data();
			final RandomAccessibleInterval<DoubleType> entropyValues = ExtractGeneLists.computeOrderedEntropy(stData, entropy, numThreads);
			container.saveEntropyValues(entropyValues, dataset, entropy);
		}

		logger.debug("Done.");

		service.shutdown();
		return null;
	}

	public static void main(final String... args) {
		final CommandLine cmd = new CommandLine(new AddEntropy());
		cmd.execute(args);
	}
}
