/**
 * These are available AWS AppSync utilities that you can use in your request and response handler.
 * For more information about the utilities that are currently implemented, see
 * https://docs.aws.amazon.com/en_us/appsync/latest/devguide/resolver-reference-overview-js.html#utility-resolvers.
 */
import { util } from '@aws-appsync/utils';

/**
 * This function is invoked before the request handler of the first AppSync function in the pipeline.
 * The resolver request handler allows you to perform some preparation logic
 * before executing the defined functions in your pipeline.
 * @param ctx - Contextual information for your resolver invocation
 */
export function request(ctx) {
  // ctx example:
  // {
  //   "arguments": {
  //     "id": "1",
  //     "value": {
  //       "name": "Climate Change"
  //     }
  //   },
  //   "identity": null,
  //   "source": null,
  //   "result": null,
  //   "request": {
  //     "headers": {
  //       "x-forwarded-for": "192.119.51.145, 15.158.45.53",
  //       "accept-encoding": "gzip,deflate",
  //       "cloudfront-is-tablet-viewer": "false",
  //       "cloudfront-viewer-country": "DE",
  //       "x-amzn-requestid": "36992825-25ee-4067-a752-e052c2bb69a2",
  //       "via": "1.1 f9e7fd4b74156e78a449b2e846941478.cloudfront.net (CloudFront)",
  //       "x-api-key": "...",
  //       "cloudfront-forwarded-proto": "https",
  //       "content-type": "application/json; charset=UTF-8",
  //       "x-amzn-trace-id": "Root=1-63fbc2d7-719023d307398084182dc2a9",
  //       "x-amz-cf-id": "2KOH5qm0_OmmTbAcLXv_fj5m-lyHN21-3-20sjtipQm3u6U-oMjzYw==",
  //       "content-length": "187",
  //       "x-forwarded-proto": "https",
  //       "host": "3vbfo5wg6vbibbmesukjlqaw74.appsync-api.eu-central-1.amazonaws.com",
  //       "user-agent": "Apache-HttpClient/4.5.13 (Java/17.0.5)",
  //       "cloudfront-is-desktop-viewer": "true",
  //       "cloudfront-is-mobile-viewer": "false",
  //       "x-forwarded-port": "443",
  //       "cloudfront-is-smarttv-viewer": "false",
  //       "cloudfront-viewer-asn": "15943"
  //     },
  //     "domainName": null
  //   },
  //   "info": {
  //     "fieldName": "publishCreatedPlanetaryBoundary",
  //     "parentTypeName": "Mutation",
  //     "variables": {}
  //   },
  //   "error": null,
  //   "prev": null,
  //   "stash": {},
  //   "outErrors": []
  // }
  return { ...ctx.arguments.value };
}

/**
 * Pipeline functions exhibit the following behaviors:
 * 1) Between your request and response handler, the functions of your pipeline resolver will run in sequence.
 * 2) The resolver's request handler result is made available to the first function as ctx.prev.result.
 * 3) Each function's response handler result is available to the next function as ctx.prev.result.
 */

/**
 * This function is invoked after the response handler of the last AppSync function in the pipeline.
 * The resolver response handler allows you to perform some final evaluation logic
 * from the output of the last function to the expected GraphQL field type.
 * @param ctx - Contextual information for your resolver invocation.
 */
export function response(ctx) {
  return ctx.prev.result;
}
