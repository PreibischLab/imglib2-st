package cmd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.N5FSReader;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import examples.VisualizeStack;
import filter.FilterFactory;
import filter.MedianFilterFactory;
import filter.SingleSpotRemovingFilterFactory;
import gui.RenderThread;
import gui.STDataAssembly;
import io.N5IO;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class DisplayStackedSlides implements Callable<Void> {

	@Option(names = {"-i", "--input"}, required = true, description = "input N5 container, e.g. -i /home/ssq.n5")
	private String input = null;

	@Option(names = {"-g", "--gene"}, required = true, description = "gene to visualize, e.g. -g Calm2")
	private String gene = null;

	@Option(names = {"-z", "--zSpacingFactor"}, required = false, description = "define the z-spacing between differnt sections (as a factor of median spacing between sequenced locations), e.g. -z 10.0 (default: 5.0)")
	private double zSpacingFactor = 5.0;

	@Option(names = {"-c", "--contrast"}, description = "comma separated contrast range for BigDataViewer display, e.g. -c '0,255' (default 0.1,5)" )
	private String contrastString = null;

	@Option(names = {"-d", "--datasets"}, required = false, description = "comma separated list of one or more datasets, e.g. -d 'Puck_180528_20,Puck_180528_22' (default: all)")
	private String datasets = null;

	@Option(names = {"-f", "--singleSpotFilter"}, required = false, description = "filter single spots using the median distance between all spots as threshold (default: false)")
	private boolean singleSpotFilter = false;

	@Option(names = {"-m", "--median"}, required = false, description = "median-filter all spots using a given radius, e.g -m 20.0 (default: no filtering)")
	private Double median = null;

	@Override
	public Void call() throws Exception {

		final boolean useIntensityTransform = false;
		final boolean useTransform = true;

		final N5FSReader n5 = N5IO.openN5( new File( input ) );

		List< String > inputDatasets;

		if ( datasets == null || datasets.length() == 0 )
			inputDatasets = N5IO.listAllDatasets( n5 );//Arrays.asList( n5.list( "/" ) );
		else
			inputDatasets = Arrays.asList( datasets.split( "," ) );

		if ( inputDatasets.size() == 0 )
		{
			System.out.println( "no input datasets available. stopping.");
			return null;
		}

		double minI = RenderThread.min;
		double maxI = RenderThread.max;

		if ( contrastString != null && contrastString.length() > 0 )
		{
			String[] contrastStrings = contrastString.trim().split( "," );

			if ( contrastStrings.length != 2 )
			{
				System.out.println( "contrast string could not parsed " + Arrays.asList( contrastStrings ) + ", ignoring - setting default range (" + minI + "," + maxI + ")" );
			}
			else
			{
				minI = Double.parseDouble( contrastStrings[ 0 ] );
				maxI = Double.parseDouble( contrastStrings[ 1 ] );
	
				System.out.println( "contrast range set to (" + minI + "," + maxI + ")" );
			}
		}

		final ArrayList< STDataAssembly > slides = new ArrayList<>();

		for ( final String dataset : inputDatasets )
		{
			final STDataAssembly st = N5IO.openDataset(n5, dataset );

			if ( !useTransform )
				st.transform().set( new AffineTransform2D() );

			if ( !useIntensityTransform )
				st.intensityTransform().set( 1, 0 );

			slides.add( st );
		}

		final DoubleType outofbounds = new DoubleType( 0 );

		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		if ( singleSpotFilter )
		{
			System.out.println( "Using single-spot filtering, radius="  + (slides.get( 0 ).statistics().getMedianDistance() * 1.5) );
			filterFactorys.add( new SingleSpotRemovingFilterFactory<>( outofbounds, slides.get( 0 ).statistics().getMedianDistance() * 1.5 ) );
		}

		if ( median != null && median > 0.0 )
		{
			System.out.println( "Using median filtering, radius=" + median );
			filterFactorys.add( new MedianFilterFactory<>( outofbounds, median ) );
		}

		final Pair< RealRandomAccessible< DoubleType >, Interval > stack = VisualizeStack.createStack( slides, gene, outofbounds, zSpacingFactor, filterFactorys );
		final Interval interval = stack.getB();

		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() );
		BdvStackSource< ? > source = BdvFunctions.show( stack.getA(), interval, gene, options );
		source.setDisplayRange( minI, maxI );
		source.setDisplayRangeBounds( 0, 200 );
		source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		source.setCurrent();

		return null;
	}

	public static final void main(final String... args) {
		CommandLine.call(new DisplayStackedSlides(), args);
	}

}
