package examples;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.InputTriggerDescription;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import align.AlignTools;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import data.STData;
import data.STDataN5;
import data.STDataStatistics;
import data.STDataUtils;
import filter.FilterFactory;
import filter.GaussianFilterFactory;
import filter.GaussianFilterFactory.WeightType;
import gui.STDataAssembly;
import ij.ImageJ;
import imglib2.StackedIterableRealInterval;
import imglib2.TransformedIterableRealInterval;
import io.N5IO;
import io.Path;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import render.Render;
import tools.BDVFlyThrough;
import tools.BDVFlyThrough.CallbackBDV;

public class VisualizeStack
{
	protected static double minRange = 0;
	protected static double maxRange = 100;
	protected static double min = 0.1;
	protected static double max = 5.5;

	public static void render2d( final STDataAssembly stdata )
	{
		final List< FilterFactory< DoubleType, DoubleType > > filterFactorys = new ArrayList<>();

		//filterFactorys.add( new MedianFilterFactory<>( new DoubleType( 0 ), 50.0 ) );
		//filterFactorys.add( new GaussianFilterFactory<>( new DoubleType( 0 ), 50.0, WeightType.BY_SUM_OF_WEIGHTS ) );
		//filterFactorys.add( new MeanFilterFactory<>( new DoubleType( 0 ), 50.0 ) );

		final String gene = "Calm2";
		final RealRandomAccessible< DoubleType > renderRRA = Render.getRealRandomAccessible( stdata, gene, 1.0, filterFactorys );

		final Interval interval =
				STDataUtils.getIterableInterval(
						new TransformedIterableRealInterval<>(
								stdata.data(),
								stdata.transform() ) );

		final BdvOptions options = BdvOptions.options().is2D().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 );

		/*
		new ImageJ();
		final RandomAccessibleInterval< DoubleType > rendered = Views.interval( Views.raster( renderRRA ), interval );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		final int geneIndex = puckData.getIndexForGene( "Pcp4" );
		expr.setValueIndex( geneIndex );
		ImageJFunctions.show( rendered, Threads.createFixedExecutorService() );
		//SimpleMultiThreading.threadHaltUnClean();
		*/

		BdvStackSource<?> bdv = BdvFunctions.show( renderRRA, interval, gene, options );
		bdv.setDisplayRange( min, max );
		bdv.setDisplayRangeBounds( minRange, maxRange );
		//bdv.setColor( new ARGBType( ARGBType.rgba( 255, 0, 0, 0 ) ) );
		//bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		bdv.setCurrent();

		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Calm1" );
		genesToTest.add( "Calm2" );
		genesToTest.add( "Hpca" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Pcp4" );

		final Random rnd = new Random();
/*
		do
		{
			SimpleMultiThreading.threadWait( 500 );

			if ( medianRadius < 100 )
				medianRadius += 3;

			if ( gaussRadius < 0 )
				gaussRadius += 3;

			if ( avgRadius < 0 )
				avgRadius += 3;

			String showGene = genesToTest.get( rnd.nextInt( genesToTest.size() ) );
			showGene = gene;
			System.out.println( showGene + ", " + medianRadius + ", " + gaussRadius + ", " + avgRadius );

			BdvStackSource<?> old = bdv;
			bdv = BdvFunctions.show(
					Render.getRealRandomAccessible( stdata, showGene, 1.0, medianRadius, gaussRadius, avgRadius ),
					interval,
					showGene,
					options.addTo( old ) );
			bdv.setDisplayRange( min, max );
			bdv.setDisplayRangeBounds( minRange, maxRange );

			old.removeFromBdv();

		} while ( System.currentTimeMillis() > 0 );
*/
	}

	public static Pair< RealRandomAccessible< DoubleType >, Interval > createStack( final List< STDataAssembly > stdata, final String gene, final DoubleType outofbounds )
	{
		final ArrayList< IterableRealInterval< DoubleType > > slices = new ArrayList<>();

		for ( int i = 0; i < stdata.size(); ++i )
			slices.add( Render.getRealIterable( stdata.get( i ), gene ) );

		final double medianDistance = stdata.get( 0 ).statistics().getMedianDistance();

		// gauss crisp
		double gaussRenderSigma = medianDistance * 1.0;
		//double gaussRenderRadius = medianDistance * 4;

		final double spacing = medianDistance * 2;

		final Interval interval2d = STDataUtils.getCommonIterableInterval( slices );
		final long[] minI = new long[] { interval2d.min( 0 ), interval2d.min( 1 ), 0 - Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final long[] maxI = new long[] { interval2d.max( 0 ), interval2d.max( 1 ), Math.round( ( stdata.size() - 1 ) * spacing ) + Math.round( Math.ceil( gaussRenderSigma * 3 ) ) };
		final Interval interval = new FinalInterval( minI, maxI );

		final StackedIterableRealInterval< DoubleType > stack = new StackedIterableRealInterval<>( slices, spacing );

		return new ValuePair<>( Render.render( stack, new GaussianFilterFactory<>( outofbounds, gaussRenderSigma*1.5, WeightType.PARTIAL_BY_SUM_OF_WEIGHTS ) ), interval );
	}

	public static void render3d( final List< STDataAssembly > stdata )
	{
		final List< String > genesToTest = new ArrayList<>();
		genesToTest.add( "Actb" );
		genesToTest.add( "Ubb" );
		genesToTest.add( "Hpca" );
		genesToTest.add( "Calm2" );
		genesToTest.add( "Mbp" );
		genesToTest.add( "Fth1" );
		genesToTest.add( "Pcp4" );
		genesToTest.add( "Ptgds" );
		genesToTest.add( "Ttr" );
		genesToTest.add( "Calm1" );
		genesToTest.add( "Fkbp1a" );

		final DoubleType outofbounds = new DoubleType( 0 );

		final Pair< RealRandomAccessible< DoubleType >, Interval > stack = createStack( stdata, genesToTest.get( 0 ), outofbounds );
		final Interval interval = stack.getB();

		final BdvOptions options = BdvOptions.options().numRenderingThreads( Runtime.getRuntime().availableProcessors() / 2 );
		BdvStackSource< ? > source = BdvFunctions.show( stack.getA(), interval, genesToTest.get( 0 ), options/*.addTo( old )*/ );
		source.setDisplayRange( min, max );
		source.setDisplayRangeBounds( minRange, maxRange );
		source.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		source.setCurrent();

		final Random rnd = new Random( 34 );

		setupRecordMovie(
				source,
				(i, oldSource) ->
				{
					if ( i % 20 == 0 && i <= 220 )
					{
						int newGene = ( i == 220 ) ? 0 : i / 20; //rnd.nextInt( genesToTest.size() );
						
						System.out.println( "Rendering: " + genesToTest.get( newGene ) );
						BdvStackSource<?> newSource = BdvFunctions.show( createStack( stdata, genesToTest.get( newGene ), outofbounds ).getA(), interval, genesToTest.get( newGene ), options );
						newSource.setDisplayRange( min, max );
						newSource.setDisplayRangeBounds( minRange, maxRange );
						newSource.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );

						if ( oldSource != null )
							oldSource.close();

						return newSource;
					}
					else
					{
						return oldSource;
					}
				} );
	}

	public static void setupRecordMovie( final BdvStackSource<?> bdvSource, final CallbackBDV callback )
	{
		final ActionMap ksActionMap = new ActionMap();
		final InputMap ksInputMap = new InputMap();

		// default input trigger config, disables "control button1" drag in bdv
		// (collides with default of "move annotation")
		final InputTriggerConfig config = new InputTriggerConfig(
				Arrays.asList(
						new InputTriggerDescription[]{
								new InputTriggerDescription(
										new String[]{"not mapped"}, "drag rotate slow", "bdv")}));

		final KeyStrokeAdder ksKeyStrokeAdder = config.keyStrokeAdder(ksInputMap, "persistence");

		new AbstractNamedAction( "Record movie" )
		{
			private static final long serialVersionUID = 3640052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				new Thread( ()-> BDVFlyThrough.record( bdvSource, callback ) ).start();
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl R" );
			}
		}.register();

		new AbstractNamedAction( "Add Current Viewer Transform" )
		{
			private static final long serialVersionUID = 3620052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				BDVFlyThrough.addCurrentViewerTransform( bdvSource.getBdvHandle().getViewerPanel() );
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl A" );
			}
		}.register();

		new AbstractNamedAction( "Clear All Viewer Transforms" )
		{
			private static final long serialVersionUID = 3620052275162419689L;

			@Override
			public void actionPerformed(ActionEvent e)
			{
				BDVFlyThrough.clearAllViewerTransform();
			}

			public void register() {
				put(ksActionMap);
				ksKeyStrokeAdder.put(name(), "ctrl X" );
			}
		}.register();

		bdvSource.getBdvHandle().getKeybindings().addActionMap("persistence", ksActionMap);
		bdvSource.getBdvHandle().getKeybindings().addInputMap("persistence", ksInputMap);
	}

	public static void visualizeIJ( final ArrayList< STDataAssembly > puckData )
	{
		new ImageJ();

		List< Pair< STData, AffineTransform2D > > data = new ArrayList<>();

		for ( final STDataAssembly stDataAssembly : puckData )
			data.add( new ValuePair<STData, AffineTransform2D>( stDataAssembly.data(), new AffineTransform2D() ) );

		AlignTools.visualizeList( data ).setTitle( "unaligned" );

		data = new ArrayList<>();

		for ( final STDataAssembly stDataAssembly : puckData )
			data.add( new ValuePair<STData, AffineTransform2D>( stDataAssembly.data(), stDataAssembly.transform() ) );

		AlignTools.visualizeList( data ).setTitle( "aligned" );
	}

	public static void main( String[] args ) throws IOException
	{
		final String path = Path.getPath();
		final File n5Path = new File( path + "slide-seq-normalized.n5" );
		final N5FSReader n5 = N5IO.openN5( n5Path );
		final List< String > pucks = N5IO.listAllDatasets( n5 );

		final ArrayList< STDataAssembly > puckData = new ArrayList<>();

		for ( final String puck : pucks )
		{
			final STDataN5 data = N5IO.readN5( n5, puck );
			final STDataStatistics stats = new STDataStatistics( data );

			final AffineTransform2D t = new AffineTransform2D();
			t.set( n5.getAttribute( n5.groupPath( puck ), "transform", double[].class ) );

			final AffineTransform i = new AffineTransform( 1 );
			double[] values =  n5.getAttribute( n5.groupPath( puck ), "intensity_transform", double[].class );
			i.set( 1,0);//values[ 0 ], values[ 1 ] );

			puckData.add( new STDataAssembly( data, stats, t, i ) );
		}

		visualizeIJ( puckData );

		if ( puckData.size() == 1 )
			render2d( puckData.get( 0 ) );
		else
			render3d( puckData );
	}
}