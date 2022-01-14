package eu.dedalus.gwtstreamer.client.std;

import eu.dedalus.gwtstreamer.client.Streamer;
import eu.dedalus.gwtstreamer.client.impl.ReadContext;
import eu.dedalus.gwtstreamer.client.impl.WriteContext;


public abstract class EnumStreamer extends Streamer {
	@Override
	public void writeObject(Object obj, WriteContext ctx)
	{
		Enum<?> e = (Enum<?>) obj;
		ctx.writeInt( e.ordinal() );
	}
	
	@Override
	public Object readObject(ReadContext ctx) {
		int val = ctx.readInt();
		return getEnumValueOf( val );
	}
	
	
	protected abstract Enum<?> getEnumValueOf( int value );
}
