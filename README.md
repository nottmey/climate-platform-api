[![Deploy All](https://github.com/nottmey/climate-platform-api/actions/workflows/deploy-all.yml/badge.svg?branch=main)](https://github.com/nottmey/climate-platform-api/actions/workflows/deploy-all.yml)
[![codecov](https://codecov.io/github/nottmey/climate-platform-api/branch/main/graph/badge.svg?token=E5ZVNBY3Z9)](https://codecov.io/github/nottmey/climate-platform-api)

"Work in Progress" prototype of a platform for visualizing and relating climate action data. Aiming to display e.g. 
CO2 emissions (in all available granularities) and the corresponding CO2 reduction plans. Making it possible to 
qualify the current state of climate action and to find actionable insights:

- Get to know the most effective measures for any emission source
- Learn about dimensions and relations between emissions sources
- Identify missing measures on any granularity
- Compare, combine and enrich different sources of emission data
- Strategize a complete conclusion how to solve the climate crisis

A Flutter UI client prototype using this backend can be found [here](https://github.com/nottmey/climate_platform_ui).

## Technical Details

### Development

```shell
# install https://github.com/evilmartians/lefthook/blob/master/docs/install.md, then do:
lefthook install
```

### Schema Concept

Bridging from Datomic
([reference](https://docs.datomic.com/cloud/schema/schema-reference.html),
[best practices](https://docs.datomic.com/cloud/best.html),
[rules of schema growth](https://blog.datomic.com/2017/01/the-ten-rules-of-schema-growth.html))
to GraphQL ([spec](https://spec.graphql.org/June2018/)):

1. The **source of truth** for the Datomic schema and the GraphQL schema is the (production) database.
2. When delivering entities via GraphQL, it should be possible to concretize semantically similar Datomic entities into different GraphQL **types** so that not all entities are delivered via a generic schema.
3. **Fields** of GraphQL types should **mimic** single Datomic **attributes** and (for `:db.type/ref`) should also be able to target specific GraphQL types in either forward and backward direction.
4. Fields and Types should be able to get another **alias**. Each alias should be able to be **deprecated**.

tl;dr

1. db is source of truth for schema
2. typed entities, config stored in db
3. typed attributes with custom names, config stored in db
4. designed for future schema accretion

### Manual Deployment

#### 1. Ions

Adapted from https://docs.datomic.com/cloud/ions-tutorial/push-and-deploy.html

using https://github.com/borkdude/jet

```shell
# 1. make sure git status is clean

# 2. then do:
push_result=$(clojure -A:ion-dev '{:op :push}' | tail -6)
echo "$push_result"
deploy_command=$(echo "$push_result" | jet --query ':deploy-command println')

# 3. then use deploy command from push result, similar to this:
deploy_result=$(eval "$deploy_command" | tail -6)
echo "$deploy_result"
status_command=$(echo "$deploy_result" | jet --query ':status-command println')

# 4. monitor status with command from deploy result, similar to this:
while
  state=$(eval "$status_command")
  echo "$state" | jet --query ':code-deploy-status println' | grep -x -F 'RUNNING'
do
  echo "running: $state"
  sleep 1
done
echo "finished: $state"
```

#### 2. App Sync Api

```shell
cdk deploy
```
