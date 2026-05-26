# distribuida_ufrn

## Build

```bash
./scripts/build.sh
```

## Fluxo HTTP com gateway

Em terminais separados:

```bash
./scripts/start-gateway.sh
```

```bash
./scripts/start-instance.sh isemail email-1 9101
```

```bash
./scripts/start-instance.sh ispassword password-1 9102
```

Teste:

```bash
curl -i -X POST http://localhost:9001/isemail \
  -H "Authorization: Bearer segredo123" \
  --data "teste@ufrn.br"
```

## Endpoints auxiliares

Os endpoints principais continuam sendo `POST /isemail` e `POST /ispassword`.
Para demonstrar recursos do middleware, o `UtilHandler` também expõe:

```bash
# Demonstra @PathParam + @QueryParam
curl -i "http://localhost:9101/validate/email?value=teste@ufrn.br" \
  -H "Authorization: Bearer segredo123"
```

```bash
# Demonstra @BodyParam + @HeaderParam
curl -i -X POST http://localhost:9101/echo \
  -H "Authorization: Bearer segredo123" \
  -H "X-Transform: upper" \
  --data "texto de exemplo"
```

```bash
# Demonstra @InstancePolicy(LifecyclePolicy.PER_REQUEST)
curl -i http://localhost:9101/demo/instance \
  -H "Authorization: Bearer segredo123"
```

O `UtilHandler` usa `LifecyclePolicy.PER_REQUEST`, então duas chamadas seguidas para
`/demo/instance` retornam `instanceId` e `createdAt` diferentes. Handlers sem
`@InstancePolicy` usam `LifecyclePolicy.STATIC` por padrão no middleware.

## Fluxo UDP pelo middleware

Suba uma instância usando o plugin UDP do middleware:

```bash
./scripts/start-instance.sh isemail email-udp-1 9101 UDP
```

Envie a requisição UDP direto para a porta da instância:

```bash
printf '{"method":"POST","path":"/isemail","headers":{"Authorization":"Bearer segredo123"},"body":"teste@ufrn.br"}' | nc -u -w 2 localhost 9101
```
