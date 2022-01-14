package eu.dedalus.gwtstreamer.client.std;

import eu.dedalus.gwtstreamer.client.Streamer;
import eu.dedalus.gwtstreamer.client.StreamerException;
import eu.dedalus.gwtstreamer.client.impl.ReadContext;
import eu.dedalus.gwtstreamer.client.impl.WriteContext;

/**
 * Used to serialize multidimensional and typed arrays 
 */
public abstract class ArrayStreamer extends Streamer
{
	@Override
	public void writeObject( Object obj, WriteContext ctx )
	{
		ctx.addObject(obj);
		writeObjectData( (Object[]) obj, ctx );
	}
	
	
	@Override
	public Object readObject( ReadContext ctx )
	{
		int length = ctx.readInt();
		if (length > ctx.getSizeLimit())
			throw new StreamerException("Array length exceeds stream size: "+length);
		Object[] obj = createObjectArrayInstance(length);
		ctx.addObject(obj);
		readObjectData( obj, ctx );
		return obj;
	}
	
	
	protected void writeObjectData( Object[] obj, WriteContext ctx )
	{
		ctx.writeInt(obj.length);
		
		for ( Object o : obj )
			ctx.writeObject(o);
	}

	
	protected void readObjectData( Object[] obj, ReadContext ctx )
	{
		for ( int i = 0; i < obj.length; i++ ) {
			obj[i] = ctx.readObject();
		}
	}
	
	
	/** Create new object array instance */
	protected abstract Object[] createObjectArrayInstance( int length );
}
