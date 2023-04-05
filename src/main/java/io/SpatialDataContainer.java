package io;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SpatialDataContainer {

	final private String rootPath;
	final private boolean readOnly;
	final private N5FSReader n5;
	private List<String> datasets = new ArrayList<>();
	final private static String version = "0.1.0";
	final private static String versionKey = "spatial_data_container";
	final private static String numDatasetsKey = "num_datasets";
	final private static String datasetsKey = "datasets";

	protected SpatialDataContainer(String path, boolean readOnly) throws IOException {
		this.rootPath = path;
		this.readOnly = readOnly;

		this.n5 = readOnly ? new N5FSReader(path) : new N5FSWriter(path);
	}

	public static SpatialDataContainer openExisting(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataContainer container = new SpatialDataContainer(path, false);
		container.readFromDisk();
		return container;
	}

	public static SpatialDataContainer openForReading(String path) throws IOException {
		if (!(new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' does not exist.");
		SpatialDataContainer container = new SpatialDataContainer(path, true);
		container.readFromDisk();
		return container;
	}

	public static SpatialDataContainer createNew(String path) throws IOException {
		if ((new File(path)).exists())
			throw new SpatialDataIOException("N5 '" + path + "' already exists.");
		SpatialDataContainer container = new SpatialDataContainer(path, false);
		container.initializeContainer();
		return container;
	}

	protected void initializeContainer() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", versionKey, version);
		writer.createGroup("/matches");
		updateDatasetMetadata();
	}

	protected void readFromDisk() throws IOException {
		String actualVersion = n5.getAttribute("/", versionKey, String.class);
		if (!this.version.equals(actualVersion))
			throw new SpatialDataIOException("Incompatible spatial data container version: expected " + version + ", got " + actualVersion + ".");

		int numDatasets = n5.getAttribute("/", numDatasetsKey, int.class);
		datasets = n5.getAttribute("/", datasetsKey, List.class);
	}

	protected void updateDatasetMetadata() throws IOException {
		N5FSWriter writer = (N5FSWriter) n5;
		writer.setAttribute("/", numDatasetsKey, datasets.size());
		writer.setAttribute("/", datasetsKey, datasets.toArray());
	}

	public void addExistingDataset(String path) throws IOException {
		Path oldPath = Paths.get(path);
		String datasetName = oldPath.getFileName().toString();

		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		if (datasets.contains(datasetName))
			throw new SpatialDataIOException("Dataset '" + datasetName + "' already exists.");

		Files.move(oldPath, Paths.get(rootPath, datasetName));
		datasets.add(datasetName);
		updateDatasetMetadata();
	}

	public void deleteDataset(String datasetName) throws IOException {
		if (readOnly)
			throw new SpatialDataIOException("Trying to modify a read-only spatial data container.");
		Files.delete(Paths.get(rootPath, datasetName));
		datasets.remove(datasetName);
		updateDatasetMetadata();
	}

	public SpatialDataIO openDataset(String datasetName) throws IOException {
		if (!datasets.contains(datasetName))
			throw new SpatialDataIOException("Container does not contain dataset '" + datasetName + "'.");
		return SpatialDataIO.inferFromName(Paths.get(rootPath, datasetName).toString());
	}

	public List<SpatialDataIO> openAllDatasets() throws IOException {
		List<SpatialDataIO> datasetIOs = new ArrayList<>();
		for (final String datasetName : datasets)
			datasetIOs.add(SpatialDataIO.inferFromName(Paths.get(rootPath, datasetName).toString()));
		return datasetIOs;
	}

	public static boolean isCompatibleContainer(String path) {
		try {
			N5FSReader reader = new N5FSReader(path);
			String actualVersion = reader.getAttribute("/", versionKey, String.class);
			return (actualVersion.equals(version));
		} catch (Exception e) {
			return false;
		}
	}

	public List<String> getDatasets() {
		return new ArrayList<>(datasets);
	}

	public String getVersion() {
		return version;
	}
}
