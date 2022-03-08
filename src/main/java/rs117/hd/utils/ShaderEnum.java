package rs117.hd.utils;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShaderEnum
{
	public static String generateDefines(String prefix, Class<?> shaderEnumClass)
	{
		StringBuilder sb = new StringBuilder();
		for (Field field : shaderEnumClass.getFields())
		{
			if (field.getType().equals(int.class))
			{
				if (!Modifier.isFinal(field.getModifiers()))
				{
					log.warn("Skipping non-final ShaderEnum field: " + field.getName());
					continue;
				}

				try
				{
					sb
						.append("#define ")
						.append(prefix)
						.append("_")
						.append(field.getName())
						.append(" ")
						.append(field.getInt(shaderEnumClass))
						.append("\n");
				}
				catch (IllegalAccessException ex)
				{
					log.warn("Unable to access ShaderEnum field: " + field.getName(), ex);
				}
			}
		}
		log.debug("Defining ShaderEnum {} with prefix '{}_':\n{}", shaderEnumClass.getName(), prefix, sb);
		return sb.toString();
	}
}
