## Deployment

### 1. Ions

Adapted from https://docs.datomic.com/cloud/ions-tutorial/push-and-deploy.html

using https://github.com/borkdude/jet

```shell
# 1. make sure git status is clean

# 2. then do:
push_result=$(clojure -A:ion-dev '{:op :push}')
echo "$push_result"
deploy_command=$(echo "$push_result" | jet --query ':deploy-command println' | grep -v -x -F 'nil')

# 3. then use deploy command from push result, similar to this:
deploy_result=$(eval "$deploy_command")
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

### 2. App Sync Api

```shell
cdk deploy
```

## Schema Concept

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