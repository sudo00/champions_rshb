import { initInfra } from "../utils/infra";

export default defineNitroPlugin(async () => {
  if (process.env.TESTING === "1") {
    return;
  }
  await initInfra();
});
