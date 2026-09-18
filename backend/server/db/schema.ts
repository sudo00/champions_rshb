import { pgTable, text, jsonb, real } from "drizzle-orm/pg-core";

/** Catalog row. Embedding stored via pgvector in SQL; Drizzle column is a placeholder. */
export const wines = pgTable("wines", {
  slug: text("slug").primaryKey(),
  title: text("title"),
  producer: text("producer"),
  region: text("region"),
  grape: text("grape"),
  description: text("description"),
  rating: real("rating"),
  payload: jsonb("payload"),
  imageKey: text("image_key"),
});
