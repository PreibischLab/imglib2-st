package io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import filter.FilterFactory;
import gui.STDataExplorer;
import net.imglib2.*;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import anndata.AnnDataUtils;
import data.STData;
import data.STDataImgLib2;
import data.STDataStatistics;
import gui.STDataAssembly;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import render.Render;

import static gui.RenderThread.*;

public class AnnDataIO
{
	private static final String celltypePath = "/obs/cell_type";
	private static final String locationPath = "/obsm/spatial";

	public static void main( String[] args ) throws IOException
	{
		final String path = System.getProperty("user.dir") + "/data/human-lymph-node.h5ad";
		final File file = new File(path);

		STDataAssembly data = AnnDataIO.openAllDatasets(new File(path));
		String gene = "IGKC";

		Interval interval = data.data().getRenderInterval();
		System.out.println( "render interval: " + Util.printInterval( interval ) );
		System.out.println( "exp value base matrix: " + Util.printInterval( data.data().getAllExprValues() ) );
		System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());

		List<FilterFactory<DoubleType, DoubleType>> filterFactories = new ArrayList<>();
		BdvOptions options = BdvOptions.options().is2D().numRenderingThreads(Runtime.getRuntime().availableProcessors());

		final RealRandomAccessible<DoubleType> renderRRA = Render.getRealRandomAccessible( data, gene, 0.2, filterFactories );
		BdvStackSource<DoubleType> bdv = BdvFunctions.show(renderRRA, interval, "", options);
//		RandomAccessibleInterval<DoubleType> exprValues = data.data().getAllExprValues();
//		BdvStackSource<DoubleType> bdv = BdvFunctions.show(exprValues, "", options);
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		bdv.setDisplayRange( min, max );

		new STDataExplorer(Arrays.asList(data));
	}

	// TODO: ALL? opens just one ...
	public static STDataAssembly openAllDatasets(final File anndataFile) throws IOException {
		STData stData = readSlideSeq(anndataFile);

		if (stData == null)
			return null;

		final STDataStatistics stat = new STDataStatistics(stData);

		final AffineTransform2D transform = new AffineTransform2D();
		final AffineTransform intensityTransform = new AffineTransform(1);
		intensityTransform.set(1, 0);

		return new STDataAssembly(stData, stat, transform, intensityTransform);
	}

	public static N5HDF5Reader openAnnData(final File anndataFile) throws IOException {
		if (!anndataFile.exists())
			throw new RuntimeException("AnnData-path '" + anndataFile.getAbsolutePath() + "' does not exist." );

		return new N5HDF5Reader(anndataFile.getAbsolutePath());
	}

	public static STData readSlideSeq(final File anndataFile) throws IOException {
		long time = System.currentTimeMillis();
		N5Reader reader = new N5HDF5Reader(anndataFile.getAbsolutePath());

		List<String> geneNames = AnnDataUtils.readAnnotation(reader, "/var/_index");
		List<String> barcodes = AnnDataUtils.readAnnotation(reader, "/obs/_index");

		final HashMap<String, Integer> geneLookup = new HashMap<>();
		for (int i = 0; i < geneNames.size(); ++i ) {
			geneLookup.put(geneNames.get(i), i);
		}

		// permute locations, since this is required by STData
		// locations is often a dense array
		RandomAccessibleInterval<LongType> locations =
				Views.permute((RandomAccessibleInterval<LongType>) AnnDataUtils.readData(reader, locationPath), 0, 1);
		final RandomAccessibleInterval<DoubleType> convertedLocations = Converters.convert(
				locations, (i, o) -> o.set(i.getRealDouble()), new DoubleType());

		// expression values is often a sparse array
		RandomAccessibleInterval<RealType> expressionVals = AnnDataUtils.readData(reader, "/X");
		final RandomAccessibleInterval<DoubleType> convertedExpressionVals = Converters.convert(
				expressionVals, (i, o) -> o.set(i.getRealDouble()), new DoubleType());

		STData stdata = new STDataImgLib2(convertedLocations, convertedExpressionVals, geneNames, barcodes, geneLookup);

		if (containsCelltypes(anndataFile)) {
			Img<IntType> celltypeIds = getCelltypeIds(reader);
			stdata.getMetaData().put("celltype", celltypeIds);
			System.out.println("Loading '" + celltypePath + "' as label 'celltype'.");
		}

		System.out.println("Parsing took " + (System.currentTimeMillis() - time) + " ms.");

		return stdata;
	}

	public static Boolean containsCelltypes(final File anndataFile) {
		try (final N5HDF5Reader n5Reader = new N5HDF5Reader(anndataFile.getAbsolutePath())) {
			return n5Reader.exists(celltypePath);
		}
		catch (IOException e) {
			return Boolean.FALSE;
		}
	}

	public static ArrayImg<IntType, IntArray> getCelltypeIds(final N5Reader reader) throws IOException {
		final List<String> cellTypes = AnnDataUtils.readAnnotation(reader, celltypePath);
		final HashMap<String, Integer> typeToIdMap = new HashMap<>();
		final int[] celltypeIds = new int[cellTypes.size()];

		// for categorical arrays, this is redundant -> use codes directly?
		for (int k = 0; k < cellTypes.size(); ++k) {
			final String type = cellTypes.get(k);
			Integer id = typeToIdMap.get(type);
			if (null == id) {
				id = typeToIdMap.size() + 1;
				typeToIdMap.put(type, id);
			}
			celltypeIds[k] = id;
		}

		return ArrayImgs.ints(celltypeIds, celltypeIds.length);
	}
}
