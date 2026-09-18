export default defineEventHandler((event) => {
  const slug = getRouterParam(event, "slug");
  return {
    slug,
    title: null,
    producer: null,
    region: null,
    grape: null,
    description: null,
    rating: null,
    pairing: null,
  };
});
