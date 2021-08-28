package meteordevelopment.addons.template;

import meteordevelopment.addons.template.modules.Printer;
import meteordevelopment.meteorclient.MeteorAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

public class TemplateAddon extends MeteorAddon {
	public static final Logger LOG = LogManager.getLogger();
	public static final Category CATEGORY = new Category("Printer", new ItemStack(Items.PINK_CARPET));

	@Override
	public void onInitialize() {
		LOG.info("Initializing Meteor Addon Template");

		// Required when using @EventHandler
		MeteorClient.EVENT_BUS.registerLambdaFactory("meteordevelopment.addons.template", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

		// Modules
		Modules.get().add(new Printer());
	}

	@Override
	public void onRegisterCategories() {
		Modules.registerCategory(CATEGORY);
	}
}
