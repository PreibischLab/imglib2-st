package test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import data.STData;
import data.STDataStatistics;
import filter.Filters;
import filter.GaussianFilterFactory;
import filter.MedianFilterFactory;
import filter.realrandomaccess.MedianRealRandomAccessible;
import imglib2.ImgLib2Util;
import importer.Parser;
import io.JsonIO;
import net.imglib2.Cursor;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RealCursor;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import render.Render;
import transform.TransformIntensities;

public class Vistools2d
{
	public static void main( String[] args ) throws IOException
	{
		/*final STData stdata = Parser.read(
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/locations.txt" ),
				new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/dge_normalized_small.txt" ) );
		*/

		long time = System.currentTimeMillis();
		final STData stdata = JsonIO.readJSON( new File( "/Users/spreibi/Documents/BIMSB/Publications/imglib2-st/patterns_examples_2d/cut.json.zip" ) );
		System.out.println( System.currentTimeMillis() - time + " ms." );

		System.out.println( stdata );

		final STDataStatistics stStats = new STDataStatistics( stdata );

		System.out.println( stStats );

		TransformIntensities.add( stdata, 1 );

		final double displayRadius = stStats.getMedianDistance() / 2.0;
		final double medianRadius = stStats.getMedianDistance() * 2.0;

		// gauss crisp
		double gaussRenderSigma = stStats.getMedianDistance() / 4; 
		double gaussRenderRadius = displayRadius;

		final DoubleType outofbounds = new DoubleType( 0 );

		List< double[] > locations = stdata.getLocationsCopy();
		System.out.println( "#locations: " + locations.size() );

		double[] values = stdata.getExpValuesCopy( "Pcp4" );

		//for ( int i = 0; i < values.length; ++i )
		//	System.out.println( i + ": " + Util.printCoordinates( locations.get( i ) ) + " >> " + values[ i ] );

		RealPointSampleList< DoubleType > data2 = new RealPointSampleList<>( 2 );

		data2.add( new RealPoint( 0,0 ), new DoubleType( 0 ) );
		data2.add( new RealPoint( 1,1 ), new DoubleType( 1 ) );
		data2.add( new RealPoint( 2,2 ), new DoubleType( 2 ) );

		final IterableRealInterval< DoubleType > data = stdata.getExprData( "Pcp4" );

		final RealCursor< DoubleType > c0 = data.localizingCursor();

		int i = 0;
		//for ( int a = 0; a < 5; ++a )
		{
			final RealCursor< DoubleType > c1 = c0.copyCursor();
			final RealCursor< DoubleType > c2 = new KDTree<>( data ).cursor();
			
			while ( c2.hasNext() )
			{
				//c1.fwd();
				final DoubleType value = c2.next();
				//System.out.println( i + " (cursor): " + c1.getDoublePosition( 0 ) + ", " + c1.getDoublePosition( 1 ) + " >> " + c1.get().get() ); 
				System.out.println( i + " (kdtree): " + c2.getDoublePosition( 0 ) + ", " + c2.getDoublePosition( 1 ) + " >> " + c2.get().get() ); 
				++i;
			}
		}

		System.exit( 0 );
		final IterableRealInterval< DoubleType > medianFiltered = Filters.filter( data, new MedianFilterFactory<>( outofbounds, medianRadius ) );//outofbounds, medianRadius );

		final RealRandomAccessible< DoubleType > median = new MedianRealRandomAccessible<>( data, outofbounds, medianRadius );

		//BdvFunctions.show( Render.render( data, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_raw", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		//BdvFunctions.show( Render.renderAvg( data, outofbounds, displayRadius * 3 ), stdata.renderInterval, "Pcp4_rawavg", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.getRenderInterval(), "Pcp4_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.getRenderInterval(), "Pcp4_median_gauss1", BdvOptions.options().is2D() ).setDisplayRange( 0, 4 );

		// gauss smooth
		gaussRenderSigma = displayRadius;
		gaussRenderRadius = displayRadius * 4;

		BdvFunctions.show( Render.render( data, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.getRenderInterval(), "Pcp4_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );
		BdvFunctions.show( Render.render( medianFiltered, new GaussianFilterFactory<>( outofbounds, gaussRenderRadius, gaussRenderSigma, false ) ), stdata.getRenderInterval(), "Pcp4_median_gauss2", BdvOptions.options().is2D() ).setDisplayRange( 0, 9 );

		//BdvFunctions.show( Render.render( medianFiltered, outofbounds, displayRadius ), stdata.renderInterval, "Pcp4_median", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );
		BdvFunctions.show( median, stdata.getRenderInterval(), "Pcp4_median_full", BdvOptions.options().is2D() ).setDisplayRange( 0, 6 );

		//final RandomAccessibleInterval< FloatType > img = Render.render( data, stdata.renderInterval, outofbounds, displayRadius );
		//new ImageJ();
		//ImageJFunctions.show( img );
	}
}