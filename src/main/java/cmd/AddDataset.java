package cmd;

import io.SpatialDataContainer;
import io.StorageSpec;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddDataset implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input dataset, e.g. -i /home/ssq.n5")
	private String inputDatasetPath = null;

	@Option(names = {"-c", "--container"}, required = true, description = "container to add dataset to; if it doesn't exist, it will be created")
	private String containerPath = null;

	@Option(names = {"-m", "--move"}, required = false, description = "flag to indicate if dataset should be moved to container; if not, it is linked")
	private boolean shouldBeMoved = false;

	@Option(names = {"-l", "--locations"}, required = false, description = "path to locations within the dataset; if not given, the standard path is assumed")
	private String locationPath = null;

	@Option(names = {"-e", "--expression-values"}, required = false, description = "path to expression values within the dataset; if not given, the standard path is assumed")
	private String exprValPath = null;

	@Option(names = {"-a", "--annotations"}, required = false, description = "path to annotations within the dataset; if not given, the standard path is assumed")
	private String annotationPath = null;


	@Override
	public Void call() throws Exception {
		if (containerPath == null) {
			System.out.println("No container defined. Stopping.");
			return null;
		}

		if (inputDatasetPath == null) {
			System.out.println("No dataset defined. Stopping.");
			return null;
		}

		ExecutorService service = Executors.newFixedThreadPool(1);
		SpatialDataContainer container = new File(containerPath).exists()
				? SpatialDataContainer.openExisting(containerPath, service)
				: SpatialDataContainer.createNew(containerPath, service);

		StorageSpec storageSpec = new StorageSpec(locationPath, exprValPath, annotationPath);

		if (shouldBeMoved)
			container.addExistingDataset(inputDatasetPath, storageSpec);
		else
			container.linkExistingDataset(inputDatasetPath, storageSpec);

		final String operation = shouldBeMoved ? "Moved" : "Linked";
		System.out.println(operation + " dataset '" + inputDatasetPath + "' to container '" + containerPath + "'.");

		return null;
	}

	public static void main(final String... args) {
		CommandLine.call(new AddDataset(), args);
	}
}
